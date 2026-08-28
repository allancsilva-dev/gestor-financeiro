WITH ranked AS (
  SELECT id, row_number() OVER (
    PARTITION BY usuario_id, rule_code, period_start, period_end
    ORDER BY (feedback IS NOT NULL) DESC, created_at DESC, id DESC
  ) AS position
  FROM assistant_recommendations
)
DELETE FROM assistant_recommendations
WHERE id IN (SELECT id FROM ranked WHERE position > 1);

ALTER TABLE assistant_recommendations ADD CONSTRAINT ux_assistant_recommendation_period_rule
  UNIQUE (usuario_id, rule_code, period_start, period_end);
