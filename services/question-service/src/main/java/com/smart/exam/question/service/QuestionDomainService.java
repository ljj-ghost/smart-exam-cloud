package com.smart.exam.question.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.id.SnowflakeIdGenerator;
import com.smart.exam.question.dto.CreatePaperRequest;
import com.smart.exam.question.dto.CreateQuestionRequest;
import com.smart.exam.question.entity.PaperEntity;
import com.smart.exam.question.entity.PaperQuestionEntity;
import com.smart.exam.question.entity.QuestionEntity;
import com.smart.exam.question.mapper.PaperMapper;
import com.smart.exam.question.mapper.PaperQuestionMapper;
import com.smart.exam.question.mapper.QuestionMapper;
import com.smart.exam.question.model.Paper;
import com.smart.exam.question.model.PaperQuestion;
import com.smart.exam.question.model.Question;
import com.smart.exam.question.model.QuestionOption;
import com.smart.exam.question.model.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionDomainService {

    private static final Logger log = LoggerFactory.getLogger(QuestionDomainService.class);
    private static final Duration IDEMPOTENT_TTL = Duration.ofSeconds(8);
    private static final Duration QUESTION_DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration QUESTION_LIST_TTL = Duration.ofMinutes(5);
    private static final Duration PAPER_DETAIL_TTL = Duration.ofMinutes(10);
    private static final String IDEMPOTENT_PREFIX = "question:idempotent:";
    private static final String QUESTION_DETAIL_PREFIX = "question:detail:";
    private static final String PAPER_DETAIL_PREFIX = "paper:detail:";
    private static final String QUESTION_LIST_PREFIX = "question:list:";
    private static final String ADMIN_SCOPE_KEY = "ADMIN";
    private static final String SPLIT_TOKEN_REGEX = "[,\\uFF0C\\s]+";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final Set<QuestionType> CHOICE_TYPES = Set.of(QuestionType.SINGLE, QuestionType.MULTI);

    private final SnowflakeIdGenerator idGenerator;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public QuestionDomainService(SnowflakeIdGenerator idGenerator,
                                 QuestionMapper questionMapper,
                                 PaperMapper paperMapper,
                                 PaperQuestionMapper paperQuestionMapper,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Question createQuestion(CreateQuestionRequest request, String createdBy) {
        protectFromDuplicateSubmission("create-question", createdBy, request);
        NormalizedQuestionPayload normalizedPayload = normalizeQuestionPayload(request);

        QuestionEntity entity = new QuestionEntity();
        entity.setId(idGenerator.nextId());
        entity.setType(request.getType().name());
        entity.setStem(trimToValue("stem", request.getStem()));
        entity.setDifficulty(request.getDifficulty());
        entity.setKnowledgePoint(trimToNull(request.getKnowledgePoint()));
        entity.setAnalysis(trimToNull(request.getAnalysis()));
        entity.setAnswer(normalizedPayload.answer());
        entity.setCreatedBy(parseLongValue("createdBy", createdBy));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setOptionsJson(writeAsJson(normalizedPayload.options()));
        questionMapper.insert(entity);

        Question question = toQuestion(entity);
        cacheQuestion(question, createdBy);
        evictQuestionListCache(createdBy);
        return question;
    }

    public Collection<Question> listQuestions(String operatorId, String role) {
        String scopeKey = resolveScopeKey(operatorId, role);
        List<Question> cached = getCachedQuestionList(scopeKey);
        if (cached != null) {
            return cached;
        }

        var query = Wrappers.lambdaQuery(QuestionEntity.class);
        if (!isAdminRole(role)) {
            query.eq(QuestionEntity::getCreatedBy, parseLongValue("userId", operatorId));
        }
        query.orderByDesc(QuestionEntity::getCreatedAt)
                .orderByDesc(QuestionEntity::getId);

        List<QuestionEntity> entities = questionMapper.selectList(query);
        List<Question> questions = entities.stream().map(this::toQuestion).toList();
        putCache(buildQuestionListCacheKey(scopeKey), questions, QUESTION_LIST_TTL);
        return questions;
    }

    public Question findQuestion(String questionId, String operatorId, String role) {
        String scopeKey = resolveScopeKey(operatorId, role);
        String cacheKey = buildQuestionDetailCacheKey(questionId, scopeKey);
        Question cached = getCache(cacheKey, Question.class);
        if (cached != null) {
            return cached;
        }

        QuestionEntity entity = questionMapper.selectById(parseLongValue("questionId", questionId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Question not found");
        }
        ensureResourceOwner(entity.getCreatedBy(), operatorId, role, "Question is not in current teacher bank");

        Question question = toQuestion(entity);
        putCache(cacheKey, question, QUESTION_DETAIL_TTL);
        return question;
    }

    @Transactional
    public Paper createPaper(CreatePaperRequest request, String createdBy, String role) {
        protectFromDuplicateSubmission("create-paper", createdBy, request);

        List<NormalizedPaperQuestion> normalizedQuestions = normalizePaperQuestions(request.getQuestions());
        Set<Long> questionIdSet = normalizedQuestions.stream()
                .map(NormalizedPaperQuestion::questionId)
                .collect(Collectors.toSet());

        var questionQuery = Wrappers.lambdaQuery(QuestionEntity.class)
                .in(QuestionEntity::getId, questionIdSet);
        if (!isAdminRole(role)) {
            questionQuery.eq(QuestionEntity::getCreatedBy, parseLongValue("createdBy", createdBy));
        }
        List<QuestionEntity> questionEntities = questionMapper.selectList(questionQuery);
        Map<Long, QuestionEntity> questionMap = questionEntities.stream()
                .collect(Collectors.toMap(QuestionEntity::getId, entity -> entity));
        if (questionMap.size() != questionIdSet.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Paper contains question outside current teacher bank");
        }
        validatePaperQuestionOrder(normalizedQuestions, questionMap);

        int total = normalizedQuestions.stream()
                .mapToInt(NormalizedPaperQuestion::score)
                .sum();

        Long paperId = idGenerator.nextId();
        PaperEntity paperEntity = new PaperEntity();
        paperEntity.setId(paperId);
        paperEntity.setName(trimToValue("name", request.getName()));
        paperEntity.setTotalScore(total);
        paperEntity.setTimeLimitMinutes(request.getTimeLimitMinutes());
        paperEntity.setCreatedBy(parseLongValue("createdBy", createdBy));
        paperEntity.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paperEntity);

        List<PaperQuestionEntity> relationEntities = new ArrayList<>();
        for (NormalizedPaperQuestion item : normalizedQuestions) {
            PaperQuestionEntity relation = new PaperQuestionEntity();
            relation.setPaperId(paperId);
            relation.setQuestionId(item.questionId());
            relation.setScore(item.score());
            relation.setOrderNo(item.orderNo());
            paperQuestionMapper.insert(relation);
            relationEntities.add(relation);
        }

        Paper paper = toPaper(paperEntity, relationEntities);
        putCache(buildPaperDetailCacheKey(paper.getId(), resolveScopeKey(createdBy, role)), paper, PAPER_DETAIL_TTL);
        return paper;
    }

    public Map<String, Object> listPapers(String operatorId, String role, String keyword, Long page, Long size) {
        String trimmedKeyword = trimToNull(keyword);
        long safePage = normalizePage(page);
        long safeSize = normalizeSize(size);
        long offset = (safePage - 1) * safeSize;

        Long operatorLongId = isAdminRole(role) ? null : parseLongValue("userId", operatorId);
        Long total = paperMapper.selectCount(buildPaperListQuery(operatorLongId, trimmedKeyword, false, 0, 0));

        List<Map<String, Object>> records = List.of();
        if (total != null && total > 0) {
            List<PaperEntity> entities = paperMapper.selectList(
                    buildPaperListQuery(operatorLongId, trimmedKeyword, true, offset, safeSize)
            );
            records = entities.stream().map(this::toPaperSummary).toList();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("total", total == null ? 0L : total);
        payload.put("records", records);
        return payload;
    }

    public Paper findPaper(String paperId, String operatorId, String role) {
        String scopeKey = resolveScopeKey(operatorId, role);
        String cacheKey = buildPaperDetailCacheKey(paperId, scopeKey);
        Paper cached = getCache(cacheKey, Paper.class);
        if (cached != null) {
            return cached;
        }

        Long paperLongId = parseLongValue("paperId", paperId);
        PaperEntity paperEntity = paperMapper.selectById(paperLongId);
        if (paperEntity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Paper not found");
        }
        ensureResourceOwner(paperEntity.getCreatedBy(), operatorId, role, "Paper is not in current teacher bank");

        List<PaperQuestionEntity> relations = paperQuestionMapper.selectList(
                Wrappers.lambdaQuery(PaperQuestionEntity.class)
                        .eq(PaperQuestionEntity::getPaperId, paperLongId)
                        .orderByAsc(PaperQuestionEntity::getOrderNo)
                        .orderByAsc(PaperQuestionEntity::getId)
        );

        Paper paper = toPaper(paperEntity, relations);
        putCache(cacheKey, paper, PAPER_DETAIL_TTL);
        return paper;
    }

    private void protectFromDuplicateSubmission(String action, String userId, Object request) {
        String cacheKey = IDEMPOTENT_PREFIX + action + ":" + userId + ":" + sha256Hex(writeAsJson(request));
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(cacheKey, "1", IDEMPOTENT_TTL);
            if (Boolean.FALSE.equals(success)) {
                throw new BizException(ErrorCode.CONFLICT, "Duplicate submission");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Skip idempotent check, key={}", cacheKey, e);
        }
    }

    private void evictQuestionListCache(String operatorId) {
        try {
            redisTemplate.delete(List.of(
                    buildQuestionListCacheKey(resolveScopeKey(operatorId, "TEACHER")),
                    buildQuestionListCacheKey(ADMIN_SCOPE_KEY)
            ));
        } catch (Exception e) {
            log.warn("Failed to evict question list cache", e);
        }
    }

    private void cacheQuestion(Question question, String operatorId) {
        putCache(buildQuestionDetailCacheKey(question.getId(), resolveScopeKey(operatorId, "TEACHER")), question, QUESTION_DETAIL_TTL);
        putCache(buildQuestionDetailCacheKey(question.getId(), ADMIN_SCOPE_KEY), question, QUESTION_DETAIL_TTL);
    }

    private Question toQuestion(QuestionEntity entity) {
        Question question = new Question();
        question.setId(String.valueOf(entity.getId()));
        question.setType(QuestionType.valueOf(entity.getType()));
        question.setStem(entity.getStem());
        question.setDifficulty(entity.getDifficulty());
        question.setKnowledgePoint(entity.getKnowledgePoint());
        question.setAnalysis(entity.getAnalysis());
        question.setAnswer(entity.getAnswer());
        question.setCreatedBy(String.valueOf(entity.getCreatedBy()));
        question.setCreatedAt(entity.getCreatedAt());
        question.setOptions(parseQuestionOptions(entity.getOptionsJson()));
        return question;
    }

    private Paper toPaper(PaperEntity paperEntity, List<PaperQuestionEntity> relations) {
        Paper paper = new Paper();
        paper.setId(String.valueOf(paperEntity.getId()));
        paper.setName(paperEntity.getName());
        paper.setTotalScore(paperEntity.getTotalScore());
        paper.setTimeLimitMinutes(paperEntity.getTimeLimitMinutes());
        paper.setCreatedBy(String.valueOf(paperEntity.getCreatedBy()));
        paper.setQuestions(relations.stream().map(this::toPaperQuestion).toList());
        return paper;
    }

    private PaperQuestion toPaperQuestion(PaperQuestionEntity entity) {
        PaperQuestion question = new PaperQuestion();
        question.setQuestionId(String.valueOf(entity.getQuestionId()));
        question.setScore(entity.getScore());
        question.setOrderNo(entity.getOrderNo());
        return question;
    }

    private List<QuestionOption> parseQuestionOptions(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<QuestionOption>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to parse question options");
        }
    }

    private NormalizedQuestionPayload normalizeQuestionPayload(CreateQuestionRequest request) {
        QuestionType type = request.getType();
        if (type == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Question type is required");
        }
        if (CHOICE_TYPES.contains(type)) {
            return normalizeChoicePayload(type, request.getOptions(), request.getAnswer());
        }
        ensureNoOptions(type, request.getOptions());
        return switch (type) {
            case JUDGE -> new NormalizedQuestionPayload(normalizeJudgeAnswer(request.getAnswer()), Collections.emptyList());
            case FILL, SHORT -> new NormalizedQuestionPayload(trimToValue("answer", request.getAnswer()), Collections.emptyList());
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported question type: " + type);
        };
    }

    private NormalizedQuestionPayload normalizeChoicePayload(QuestionType type, List<QuestionOption> options, String answer) {
        List<QuestionOption> normalizedOptions = normalizeChoiceOptions(options);
        Set<String> optionKeys = normalizedOptions.stream()
                .map(QuestionOption::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> answerTokens = parseChoiceAnswer(answer);
        if (type == QuestionType.SINGLE && answerTokens.size() != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "SINGLE question must have exactly one answer option");
        }
        if (!optionKeys.containsAll(answerTokens)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Answer contains option key that does not exist");
        }
        return new NormalizedQuestionPayload(String.join(",", answerTokens), normalizedOptions);
    }

    private List<QuestionOption> normalizeChoiceOptions(List<QuestionOption> options) {
        if (options == null || options.size() < 2) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Choice question must contain at least two options");
        }

        List<QuestionOption> normalized = new ArrayList<>(options.size());
        Set<String> optionKeys = new HashSet<>();
        for (QuestionOption option : options) {
            if (option == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Question option cannot be null");
            }
            String key = normalizeChoiceToken(option.getKey());
            String text = trimToValue("option.text", option.getText());
            if (!optionKeys.add(key)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Duplicate option key: " + key);
            }

            QuestionOption normalizedOption = new QuestionOption();
            normalizedOption.setKey(key);
            normalizedOption.setText(text);
            normalized.add(normalizedOption);
        }
        return normalized;
    }

    private List<String> parseChoiceAnswer(String rawAnswer) {
        if (!StringUtils.hasText(rawAnswer)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "answer cannot be blank");
        }
        String[] tokens = rawAnswer.trim().split(SPLIT_TOKEN_REGEX);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : tokens) {
            String value = normalizeChoiceToken(token);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "answer cannot be blank");
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeChoiceToken(String rawToken) {
        return trimToValue("option.key", rawToken).toUpperCase(Locale.ROOT);
    }

    private void ensureNoOptions(QuestionType type, List<QuestionOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        throw new BizException(ErrorCode.BAD_REQUEST, type + " question cannot contain options");
    }

    private String normalizeJudgeAnswer(String rawAnswer) {
        String normalized = trimToValue("answer", rawAnswer).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "t", "yes", "y" -> "true";
            case "false", "0", "f", "no", "n" -> "false";
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "JUDGE answer must be true or false");
        };
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaperEntity> buildPaperListQuery(
            Long operatorLongId,
            String keyword,
            boolean withSortAndLimit,
            long offset,
            long size) {
        var query = Wrappers.lambdaQuery(PaperEntity.class);
        if (operatorLongId != null) {
            query.eq(PaperEntity::getCreatedBy, operatorLongId);
        }
        if (StringUtils.hasText(keyword)) {
            query.like(PaperEntity::getName, keyword);
        }
        if (withSortAndLimit) {
            query.orderByDesc(PaperEntity::getCreatedAt)
                    .orderByDesc(PaperEntity::getId)
                    .last("limit " + offset + "," + size);
        }
        return query;
    }

    private Map<String, Object> toPaperSummary(PaperEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId() == null ? null : String.valueOf(entity.getId()));
        payload.put("name", entity.getName());
        payload.put("totalScore", entity.getTotalScore());
        payload.put("timeLimitMinutes", entity.getTimeLimitMinutes());
        payload.put("createdBy", entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        payload.put("createdAt", entity.getCreatedAt());
        return payload;
    }

    private List<NormalizedPaperQuestion> normalizePaperQuestions(List<PaperQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Paper questions cannot be empty");
        }

        Set<Long> questionIdSet = new HashSet<>();
        Set<Integer> orderNoSet = new HashSet<>();
        List<NormalizedPaperQuestion> normalized = new ArrayList<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            PaperQuestion item = questions.get(i);
            if (item == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Paper question item cannot be null");
            }
            long questionId = parseLongValue("questionId", item.getQuestionId());
            if (!questionIdSet.add(questionId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Duplicate questionId in paper: " + questionId);
            }

            if (item.getScore() == null || item.getScore() < 1) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Question score must be greater than 0");
            }

            int orderNo = item.getOrderNo() == null ? i + 1 : item.getOrderNo();
            if (orderNo < 1) {
                throw new BizException(ErrorCode.BAD_REQUEST, "orderNo must be greater than 0");
            }
            if (!orderNoSet.add(orderNo)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Duplicate orderNo in paper: " + orderNo);
            }
            normalized.add(new NormalizedPaperQuestion(questionId, item.getScore(), orderNo));
        }

        normalized.sort(Comparator.comparingInt(NormalizedPaperQuestion::orderNo));
        return normalized;
    }

    private void validatePaperQuestionOrder(List<NormalizedPaperQuestion> questions, Map<Long, QuestionEntity> questionMap) {
        int previousRank = Integer.MIN_VALUE;
        for (NormalizedPaperQuestion question : questions) {
            QuestionEntity questionEntity = questionMap.get(question.questionId());
            if (questionEntity == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Question not found in paper: " + question.questionId());
            }
            int currentRank = resolveQuestionRank(questionEntity.getType());
            if (currentRank < previousRank) {
                throw new BizException(
                        ErrorCode.BAD_REQUEST,
                        "Paper question order must be: CHOICE(SINGLE/MULTI) -> JUDGE -> FILL -> SHORT"
                );
            }
            previousRank = currentRank;
        }
    }

    private int resolveQuestionRank(String rawQuestionType) {
        QuestionType type;
        try {
            type = QuestionType.valueOf(trimToValue("question.type", rawQuestionType).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported question type in paper: " + rawQuestionType);
        }

        return switch (type) {
            case SINGLE, MULTI -> 1;
            case JUDGE -> 2;
            case FILL -> 3;
            case SHORT -> 4;
        };
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String trimToValue(String fieldName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private <T> T getCache(String cacheKey, Class<T> clazz) {
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cachedJson)) {
                return null;
            }
            return objectMapper.readValue(cachedJson, clazz);
        } catch (Exception e) {
            log.warn("Failed to read cache, key={}", cacheKey, e);
            return null;
        }
    }

    private List<Question> getCachedQuestionList(String scopeKey) {
        try {
            String cachedJson = redisTemplate.opsForValue().get(buildQuestionListCacheKey(scopeKey));
            if (!StringUtils.hasText(cachedJson)) {
                return null;
            }
            return objectMapper.readValue(cachedJson, new TypeReference<List<Question>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to read question list cache", e);
            return null;
        }
    }

    private void putCache(String cacheKey, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(cacheKey, writeAsJson(value), ttl);
        } catch (Exception e) {
            log.warn("Failed to write cache, key={}", cacheKey, e);
        }
    }

    private String writeAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to serialize payload");
        }
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SHA-256 is not available");
        }
    }

    private long parseLongValue(String fieldName, String rawValue) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid " + fieldName + ": " + rawValue);
        }
    }

    private long normalizePage(Long page) {
        if (page == null || page < 1) {
            return 1L;
        }
        return page;
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return 20L;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void ensureResourceOwner(Long ownerId, String operatorId, String role, String message) {
        if (isAdminRole(role)) {
            return;
        }
        long operatorLongId = parseLongValue("userId", operatorId);
        if (ownerId == null || ownerId != operatorLongId) {
            throw new BizException(ErrorCode.FORBIDDEN, message);
        }
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(String.valueOf(role).trim());
    }

    private String resolveScopeKey(String operatorId, String role) {
        return isAdminRole(role) ? ADMIN_SCOPE_KEY : parseLongValue("userId", operatorId) + "";
    }

    private String buildQuestionListCacheKey(String scopeKey) {
        return QUESTION_LIST_PREFIX + scopeKey;
    }

    private String buildQuestionDetailCacheKey(String questionId, String scopeKey) {
        return QUESTION_DETAIL_PREFIX + scopeKey + ":" + questionId;
    }

    private String buildPaperDetailCacheKey(String paperId, String scopeKey) {
        return PAPER_DETAIL_PREFIX + scopeKey + ":" + paperId;
    }

    private record NormalizedQuestionPayload(String answer, List<QuestionOption> options) {
    }

    private record NormalizedPaperQuestion(long questionId, int score, int orderNo) {
    }
}
