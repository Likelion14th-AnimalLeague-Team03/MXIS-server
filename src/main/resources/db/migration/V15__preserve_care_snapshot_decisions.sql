ALTER TABLE care_reports
    DROP CONSTRAINT ck_care_reports_grade,
    ADD CONSTRAINT ck_care_reports_grade
        CHECK (condition_grade IN ('COLLECTING_DATA', 'STABLE', 'BALANCED', 'LIGHT_CARE', 'EXPERT_CHECK')),
    ADD COLUMN sensor_revision BIGINT NULL COMMENT 'Maximum sensor reading ID included in this snapshot',
    ADD INDEX idx_care_reports_product_window_end (product_id, analysis_window_days, period_end, id);

-- The old entity omitted these columns; their defaults were not the actual AI decisions.
-- Backfill only values present in the saved response, without fabricating a score from a grade.
UPDATE care_reports
SET analysis_window_days = GREATEST(1, TIMESTAMPDIFF(DAY, period_start, period_end)),
    data_status = CASE
        WHEN JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.dataSufficiency.status'))
             IN ('NO_DATA', 'INSUFFICIENT_DATA', 'STALE_DATA', 'SUFFICIENT')
        THEN JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.dataSufficiency.status'))
        ELSE 'INSUFFICIENT_DATA' END,
    condition_label = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.productCondition.label')), 'null'),
    condition_score = CASE
        WHEN JSON_TYPE(JSON_EXTRACT(ai_output, '$.aiCareSummary.productCondition.score')) = 'INTEGER'
         AND CAST(JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.productCondition.score')) AS SIGNED) BETWEEN 0 AND 100
        THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.productCondition.score')) AS SIGNED)
        ELSE NULL END,
    primary_factor = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.productCondition.primaryFactor')), 'null'),
    care_need = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.careDecision.careNeed')), 'null'),
    inspection_need = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ai_output, '$.aiCareSummary.careDecision.inspectionNeed')), 'null');

UPDATE care_reports
SET data_status = CASE WHEN data_status = 'SUFFICIENT' THEN 'INSUFFICIENT_DATA' ELSE data_status END,
    condition_grade = 'COLLECTING_DATA', condition_label = 'Collecting Data', condition_score = NULL
WHERE data_status <> 'SUFFICIENT' OR condition_score IS NULL
   OR condition_label IS NULL OR condition_label NOT IN ('Excellent', 'Standard', 'Needs Attention');

UPDATE care_reports
SET condition_grade = CASE
    WHEN inspection_need = 'REQUIRED' OR care_need = 'HIGH' THEN 'EXPERT_CHECK'
    WHEN inspection_need = 'CONDITIONAL' OR care_need IN ('MEDIUM', 'MEDIUM_HIGH')
         OR condition_label = 'Needs Attention' THEN 'LIGHT_CARE'
    WHEN care_need = 'LOW_MEDIUM' OR condition_label = 'Standard' THEN 'BALANCED'
    ELSE 'STABLE' END
WHERE data_status = 'SUFFICIENT' AND condition_score IS NOT NULL;

-- An active suggestion must belong to the newest usable 30-day snapshot.
UPDATE care_suggestions cs
JOIN care_reports cr ON cr.id = cs.care_report_id
SET cs.status = 'EXPIRED'
WHERE cs.status = 'ACTIVE'
  AND (cs.expires_at <= CURRENT_TIMESTAMP OR cr.data_status <> 'SUFFICIENT'
       OR cr.condition_grade NOT IN ('LIGHT_CARE', 'EXPERT_CHECK')
       OR EXISTS (SELECT 1 FROM care_reports newer
                  WHERE newer.product_id = cr.product_id AND newer.analysis_window_days = 30
                    AND (newer.period_end > cr.period_end
                         OR (newer.period_end = cr.period_end AND newer.id > cr.id))));
