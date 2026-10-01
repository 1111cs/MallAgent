-- Read-only Fixture Audit v1.0. Run after the fixed seed is loaded.
-- Every SELECT should return zero rows. Do not treat empty tables as a frozen fixture.
SET search_path = huawei_mall_mock, pg_catalog;

-- Required principals and the Normal Tool price target.
SELECT 'MISSING_PRINCIPAL' AS issue, required.id AS entity
FROM (VALUES ('U001'), ('U002'), ('U003')) AS required(id)
LEFT JOIN t_user u ON u.id = required.id
WHERE u.id IS NULL;

SELECT 'TOOL_04_MODEL_NOT_UNIQUE' AS issue, 'MateBook X Pro 2026' AS entity
WHERE (SELECT count(*) FROM t_product WHERE model = 'MateBook X Pro 2026') <> 1;

-- August 2026 is the fixed-clock interpretation of LAST_MONTH.
WITH watch_orders AS (
    SELECT o.user_id, count(DISTINCT oi.id) AS watch_items
    FROM t_order o
    JOIN t_order_item oi ON oi.order_id = o.id
    JOIN t_product p ON p.id = oi.product_id
    WHERE o.created_at >= timestamptz '2026-08-01 00:00:00+08'
      AND o.created_at <  timestamptz '2026-09-01 00:00:00+08'
      AND p.category = 'wearable'
      AND o.status NOT IN ('CANCELLED','PENDING_PAYMENT')
    GROUP BY o.user_id
)
SELECT 'WATCH_CARDINALITY' AS issue, required.user_id AS entity
FROM (VALUES ('U002', 2), ('U003', 1)) AS required(user_id, minimum)
LEFT JOIN watch_orders w ON w.user_id = required.user_id
WHERE (required.user_id = 'U002' AND coalesce(w.watch_items, 0) < required.minimum)
   OR (required.user_id = 'U003' AND coalesce(w.watch_items, 0) <> required.minimum);

-- The unique U003 watch needs a real, owned SN for getWarrantyStatus.
SELECT 'DEP_WATCH_SN_MISSING' AS issue, oi.id AS entity
FROM t_order o
JOIN t_order_item oi ON oi.order_id = o.id
JOIN t_product p ON p.id = oi.product_id
WHERE o.user_id = 'U003'
  AND o.created_at >= timestamptz '2026-08-01 00:00:00+08'
  AND o.created_at <  timestamptz '2026-09-01 00:00:00+08'
  AND p.category = 'wearable'
  AND o.status NOT IN ('CANCELLED','PENDING_PAYMENT')
  AND (oi.device_sn IS NULL OR NOT EXISTS (
      SELECT 1 FROM t_device d
      WHERE d.sn = oi.device_sn AND d.order_item_id = oi.id AND d.user_id = o.user_id
  ));

-- Cross-table relations cannot all be expressed as single-column foreign keys.
SELECT 'DEVICE_ORDER_MISMATCH' AS issue, d.sn AS entity
FROM t_device d
JOIN t_order_item oi ON oi.id = d.order_item_id
JOIN t_order o ON o.id = oi.order_id
WHERE d.user_id <> o.user_id
   OR d.product_id <> oi.product_id
   OR d.sn IS DISTINCT FROM oi.device_sn;

SELECT 'ORDER_ITEM_SN_WITHOUT_DEVICE' AS issue, oi.id AS entity
FROM t_order_item oi
WHERE oi.device_sn IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_device d WHERE d.order_item_id = oi.id AND d.sn = oi.device_sn);

SELECT 'REPAIR_OWNER_MISMATCH' AS issue, r.service_no AS entity
FROM t_repair_order r
JOIN t_device d ON d.sn = r.device_sn
WHERE r.user_id <> d.user_id;

SELECT 'ORDER_TOTAL_MISMATCH' AS issue, o.order_no AS entity
FROM t_order o
LEFT JOIN (
    SELECT order_id, sum(price * quantity) AS item_total
    FROM t_order_item GROUP BY order_id
) s ON s.order_id = o.id
WHERE s.item_total IS NULL OR o.total_amount <> s.item_total;

-- Logistics and repair events must refer to facts available by the fixed clock.
SELECT 'LOGISTICS_EVENT_INVALID' AS issue, e.id AS entity
FROM t_logistics_event e
JOIN t_order o ON o.id = e.order_id
WHERE o.logistics_tracking_no IS NULL
   OR e.event_time < o.created_at
   OR e.event_time > timestamptz '2026-09-14 10:00:00+08';

SELECT 'REPAIR_EVENT_INVALID' AS issue, e.id AS entity
FROM t_repair_progress_event e
JOIN t_repair_order r ON r.id = e.repair_order_id
WHERE e.event_time < r.created_at
   OR e.event_time > timestamptz '2026-09-14 10:00:00+08';

SELECT 'REPAIR_LAST_EVENT_MISMATCH' AS issue, r.service_no AS entity
FROM t_repair_order r
JOIN LATERAL (
    SELECT e.status FROM t_repair_progress_event e
    WHERE e.repair_order_id = r.id
    ORDER BY e.event_time DESC, e.id DESC LIMIT 1
) latest ON true
WHERE latest.status <> r.status;

SELECT 'LOGISTICS_ORDER_STATUS_MISMATCH' AS issue, o.order_no AS entity
FROM t_order o
WHERE (o.logistics_status = 'DELIVERED' AND o.status NOT IN ('DELIVERED','COMPLETED'))
   OR (o.status IN ('SHIPPED','DELIVERED','COMPLETED') AND o.logistics_tracking_no IS NULL);

-- A fixed current screen-repair price must exist for the Normal Tool case.
SELECT 'TOOL_04_SCREEN_PRICE_MISSING' AS issue, p.id AS entity
FROM t_product p
WHERE p.model = 'MateBook X Pro 2026'
  AND NOT EXISTS (
      SELECT 1 FROM t_spare_part_price s
      WHERE s.product_id = p.id AND s.part_code = 'screen'
        AND s.region = 'CN_DEFAULT'
        AND s.effective_at <= timestamptz '2026-09-14 10:00:00+08'
  );

-- Inventory, coupon and spare price lookups require unambiguous active facts.
SELECT 'ACTIVE_PRODUCT_WITHOUT_DEFAULT_STOCK' AS issue, p.id AS entity
FROM t_product p
WHERE p.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM t_inventory i WHERE i.product_id = p.id AND i.region = 'CN_DEFAULT'
  );

SELECT 'COUPON_CLAIM_OUTSIDE_PERIOD' AS issue, uc.id AS entity
FROM t_user_coupon uc
JOIN t_coupon c ON c.id = uc.coupon_id
WHERE uc.claimed_at < c.start_time OR uc.claimed_at >= c.end_time;

SELECT 'SPARE_PRICE_IN_FUTURE' AS issue, s.id AS entity
FROM t_spare_part_price s
WHERE s.effective_at > timestamptz '2026-09-14 10:00:00+08';
