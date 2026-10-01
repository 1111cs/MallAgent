-- Read-only machine checks for facts in case_facts.json. Every SELECT returns zero rows.
SET search_path = huawei_mall_mock, pg_catalog;

SELECT 'CASE_COUNT_OR_ID' AS issue
WHERE (SELECT count(*) FROM t_user WHERE id IN ('U001','U002','U003')) <> 3;

SELECT 'NORMAL_ORDER_OR_LOGISTICS' AS issue
WHERE NOT EXISTS (
  SELECT 1 FROM t_order o WHERE o.order_no='202609140001' AND o.user_id='U001'
    AND o.status='SHIPPED' AND o.logistics_tracking_no='SF202609140001'
    AND o.logistics_status='IN_TRANSIT'
    AND (SELECT count(*) FROM t_logistics_event e WHERE e.order_id=o.id)=2
);

SELECT 'NORMAL_WARRANTY' AS issue
WHERE NOT EXISTS (
  SELECT 1 FROM t_device d WHERE d.sn='ABC001' AND d.user_id='U001'
    AND d.warranty_start <= date '2026-09-14' AND d.warranty_end >= date '2026-09-14'
    AND d.warranty_end=date '2027-07-09'
);

SELECT 'NORMAL_SCREEN_PRICE' AS issue
WHERE NOT EXISTS (
  SELECT 1 FROM t_product p
  JOIN t_spare_part_price s ON s.product_id=p.id
  WHERE p.id='P-LAPTOP-001' AND p.model='MateBook X Pro 2026'
    AND s.part_code='screen' AND s.region='CN_DEFAULT'
    AND s.effective_at=timestamptz '2026-09-01 00:00+08'
    AND s.part_price=2999.00 AND s.labor_fee=300.00
    AND s.part_price+s.labor_fee=3299.00
);

SELECT 'NORMAL_REPAIR' AS issue
WHERE NOT EXISTS (
  SELECT 1 FROM t_repair_order r WHERE r.service_no='WX202609001'
    AND r.user_id='U001' AND r.status='REPAIRING'
    AND (SELECT count(*) FROM t_repair_progress_event e WHERE e.repair_order_id=r.id)=3
);

SELECT 'MISSING_SCENARIO_CARDINALITY' AS issue
WHERE (SELECT count(*) FROM t_repair_order WHERE user_id='U001')<>2
   OR (SELECT count(*) FROM t_device WHERE user_id='U001')<>2
   OR (SELECT count(*) FROM t_user_coupon WHERE user_id='U001' AND status='UNUSED')<>2
   OR (SELECT count(*) FROM t_inventory WHERE region='GUANGZHOU')<>20;

WITH watch AS (
  SELECT o.order_no,oi.device_sn FROM t_order o
  JOIN t_order_item oi ON oi.order_id=o.id
  JOIN t_product p ON p.id=oi.product_id
  WHERE o.user_id='U002' AND p.category='wearable'
    AND o.created_at>=timestamptz '2026-08-01 00:00+08'
    AND o.created_at<timestamptz '2026-09-01 00:00+08'
)
SELECT 'AMB_WATCH_CARDINALITY' AS issue
WHERE (SELECT count(*) FROM watch)<>2
   OR NOT EXISTS (SELECT 1 FROM watch WHERE order_no='202608100001' AND device_sn='WATCH-SN-002-GT')
   OR NOT EXISTS (SELECT 1 FROM watch WHERE order_no='202608200001' AND device_sn='WATCH-SN-002-FIT');

SELECT 'AMB_LAPTOP_CARDINALITY' AS issue
WHERE (SELECT count(DISTINCT d.product_id) FROM t_device d
       JOIN t_product p ON p.id=d.product_id
       WHERE d.user_id='U002' AND p.category='laptop')<>2
   OR NOT EXISTS (SELECT 1 FROM t_device WHERE user_id='U002' AND product_id='P-LAPTOP-001')
   OR NOT EXISTS (SELECT 1 FROM t_device WHERE user_id='U002' AND product_id='P-LAPTOP-002');

SELECT 'AMB_COUPON_CARDINALITY' AS issue
WHERE (SELECT count(*) FROM t_user_coupon WHERE user_id='U002' AND status='UNUSED')<>2
   OR NOT EXISTS (SELECT 1 FROM t_user_coupon WHERE user_id='U002' AND coupon_id='C001' AND status='UNUSED')
   OR NOT EXISTS (SELECT 1 FROM t_user_coupon WHERE user_id='U002' AND coupon_id='C002' AND status='UNUSED');

WITH dep_orders AS (
  SELECT o.order_no,p.category,oi.product_id,oi.device_sn
  FROM t_order o JOIN t_order_item oi ON oi.order_id=o.id
  JOIN t_product p ON p.id=oi.product_id
  WHERE o.user_id='U003' AND o.created_at>=timestamptz '2026-08-01 00:00+08'
    AND o.created_at<timestamptz '2026-09-01 00:00+08'
)
SELECT 'DEP_ORDER_CARDINALITY' AS issue
WHERE (SELECT count(*) FROM dep_orders WHERE category='wearable')<>1
   OR (SELECT count(*) FROM dep_orders WHERE category='laptop')<>1
   OR NOT EXISTS (SELECT 1 FROM dep_orders WHERE category='wearable'
                  AND order_no='202608120001' AND product_id='P-WATCH-001' AND device_sn='WATCH-SN-003')
   OR NOT EXISTS (SELECT 1 FROM dep_orders WHERE category='laptop'
                  AND order_no='202608180001' AND product_id='P-LAPTOP-001');

SELECT 'DEP_WARRANTY_OR_PRICE' AS issue
WHERE NOT EXISTS (SELECT 1 FROM t_device d WHERE d.user_id='U003' AND d.sn='WATCH-SN-003'
                  AND d.warranty_start<=date '2026-09-14' AND d.warranty_end>=date '2026-09-14')
   OR NOT EXISTS (SELECT 1 FROM t_spare_part_price s WHERE s.product_id='P-WATCH-001'
                  AND s.part_code='screen' AND s.region='CN_DEFAULT'
                  AND s.effective_at<=timestamptz '2026-09-14 10:00+08'
                  AND s.part_price+s.labor_fee=599.00);
