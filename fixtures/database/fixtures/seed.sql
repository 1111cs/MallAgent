-- Huawei Mall fixture draft v1.0; fixed clock 2026-09-14 10:00:00+08.
-- Load only once into an empty huawei_mall_mock schema. No random values or public writes.
BEGIN;
SET LOCAL search_path = huawei_mall_mock, pg_catalog;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM t_user)
       OR EXISTS (SELECT 1 FROM t_product)
       OR EXISTS (SELECT 1 FROM t_order) THEN
        RAISE EXCEPTION 'Huawei Mall Mock schema is not empty; seed aborted';
    END IF;
END $$;

INSERT INTO t_user (id,nickname,phone,region,created_at) VALUES
('U001','用户一','13800000001','BEIJING','2025-05-01 09:00+08'),
('U002','用户二','13900000002','SHANGHAI','2025-05-01 09:00+08'),
('U003','用户三','13700000003','GUANGZHOU','2025-05-01 09:00+08'),
('U004','验收用户四','13600000004','CN_DEFAULT','2025-05-01 09:00+08');

INSERT INTO t_product
(id,product_name,category,series,model,current_price,original_price,status,spec_json,created_at,updated_at) VALUES
('P-PHONE-001','HUAWEI Mate 80 Pro','phone','Mate','Mate 80 Pro',5999.00,6499.00,'ACTIVE','{"screen_size":"6.9","memory":"12GB","storage":"512GB"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-PHONE-002','HUAWEI Pura 80 Pro','phone','Pura','Pura 80 Pro',6499.00,6999.00,'ACTIVE','{"screen_size":"6.8","memory":"12GB","storage":"512GB"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-PHONE-003','HUAWEI nova 14','phone','nova','nova 14',2999.00,3299.00,'ACTIVE','{"screen_size":"6.7","memory":"8GB","storage":"256GB"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-TABLET-001','HUAWEI MatePad Pro 13.2','tablet','MatePad','MatePad Pro 13.2',4999.00,5499.00,'ACTIVE','{"screen_size":"13.2","storage":"512GB"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-TABLET-002','HUAWEI MatePad Air','tablet','MatePad','MatePad Air',2999.00,3299.00,'ACTIVE','{"screen_size":"11.5","storage":"256GB"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-TABLET-003','HUAWEI MatePad 11.5','tablet','MatePad','MatePad 11.5',1999.00,2299.00,'ACTIVE','{"screen_size":"11.5","storage":"128GB"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-LAPTOP-001','HUAWEI MateBook X Pro 2026','laptop','MateBook','MateBook X Pro 2026',12999.00,13999.00,'ACTIVE','{"screen_size":"14.2","memory":"32GB","storage":"1TB","weight":"1.26kg","usage":["coding","office","business_travel","video_editing"],"requirements":["lightweight","long_battery","high_memory","portable"]}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-LAPTOP-002','HUAWEI MateBook 14 2026','laptop','MateBook','MateBook 14 2026',6999.00,7499.00,'ACTIVE','{"screen_size":"14.0","memory":"16GB","storage":"512GB","weight":"1.49kg","usage":["coding","office","study"],"requirements":["long_battery","portable"]}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-LAPTOP-003','HUAWEI MateBook D 16','laptop','MateBook','MateBook D 16',4999.00,5499.00,'ACTIVE','{"screen_size":"16.0","memory":"16GB","storage":"512GB","weight":"1.72kg","usage":["office","study"],"requirements":["large_screen"]}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-LAPTOP-004','HUAWEI MateBook X 2026','laptop','MateBook','MateBook X 2026',9999.00,10999.00,'ACTIVE','{"screen_size":"14.0","memory":"16GB","storage":"1TB","weight":"1.18kg","usage":["coding","business_travel"],"requirements":["lightweight","portable"]}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-WATCH-001','HUAWEI WATCH GT 6','wearable','WATCH GT','WATCH GT 6',2499.00,2699.00,'ACTIVE','{"screen_size":"1.47","battery_days":"14"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-WATCH-002','HUAWEI WATCH Fit 4','wearable','WATCH Fit','WATCH Fit 4',899.00,999.00,'ACTIVE','{"screen_size":"1.82","battery_days":"10"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-WATCH-003','HUAWEI WATCH 5','wearable','WATCH','WATCH 5',2999.00,3299.00,'ACTIVE','{"screen_size":"1.5","battery_days":"7"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-HEADPHONE-001','HUAWEI FreeBuds Pro 4','headphone','FreeBuds','FreeBuds Pro 4',1499.00,1699.00,'ACTIVE','{"noise_canceling":true,"battery_hours":"30"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-HEADPHONE-002','HUAWEI FreeBuds 6i','headphone','FreeBuds','FreeBuds 6i',599.00,699.00,'ACTIVE','{"noise_canceling":true,"battery_hours":"35"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-HEADPHONE-003','HUAWEI FreeClip 2','headphone','FreeClip','FreeClip 2',1699.00,1899.00,'ACTIVE','{"open_ear":true,"battery_hours":"36"}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-ROUTER-001','HUAWEI WiFi 7 路由器','router','WiFi','WiFi 7',699.00,799.00,'ACTIVE','{"wifi_standard":"7","mesh":false}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-ROUTER-002','HUAWEI Q6 Mesh','router','Q','Q6 Mesh',1299.00,1499.00,'ACTIVE','{"wifi_standard":"6","mesh":true}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-ROUTER-003','HUAWEI AX3','router','AX','AX3',299.00,399.00,'ACTIVE','{"wifi_standard":"6","mesh":false}','2026-05-01 09:00+08','2026-09-01 09:00+08'),
('P-ROUTER-004','HUAWEI AX3 高配版','router','AX','AX3',399.00,499.00,'ACTIVE','{"wifi_standard":"6","mesh":false,"variant":"high"}','2026-05-01 09:00+08','2026-09-01 09:00+08');

-- Fixed national stock for every active SPU; representative city stock adds zero-stock paths.
INSERT INTO t_inventory (id,product_id,region,stock,sale_price,promotion_text,updated_at)
SELECT 'INV-' || p.id || '-CN',p.id,'CN_DEFAULT',20,p.current_price,'固定 Mock 全国库存','2026-09-14 09:00+08'
FROM t_product p WHERE p.status='ACTIVE';
INSERT INTO t_inventory (id,product_id,region,stock,sale_price,promotion_text,updated_at)
SELECT 'INV-' || p.id || '-GZ',p.id,'GUANGZHOU',
       CASE WHEN p.id IN ('P-LAPTOP-001','P-WATCH-002') THEN 0 ELSE 8 END,
       p.current_price,NULL,'2026-09-14 09:00+08'
FROM t_product p WHERE p.status='ACTIVE';
INSERT INTO t_inventory (id,product_id,region,stock,sale_price,promotion_text,updated_at)
SELECT 'INV-' || p.id || '-BJ',p.id,'BEIJING',12,p.current_price,NULL,'2026-09-14 09:00+08'
FROM t_product p WHERE p.status='ACTIVE';
INSERT INTO t_inventory (id,product_id,region,stock,sale_price,promotion_text,updated_at)
SELECT 'INV-' || p.id || '-SH',p.id,'SHANGHAI',5,p.current_price,NULL,'2026-09-14 09:00+08'
FROM t_product p WHERE p.status='ACTIVE';

INSERT INTO t_order
(id,order_no,user_id,status,total_amount,receiver_region,receiver_address_masked,
 logistics_carrier,logistics_tracking_no,logistics_status,estimated_delivery_time,created_at,updated_at) VALUES
('O-U001-001','202609140001','U001','SHIPPED',5999.00,'BEIJING','北京市朝阳区***号','顺丰速运','SF202609140001','IN_TRANSIT','2026-09-16 18:00+08','2026-09-14 07:20+08','2026-09-14 09:20+08'),
('O-U001-002','202607100001','U001','DELIVERED',12999.00,'BEIJING','北京市朝阳区***号','顺丰速运','SF202607100001','DELIVERED','2026-07-13 18:00+08','2026-07-10 09:00+08','2026-07-13 16:00+08'),
('O-U001-003','202606150001','U001','DELIVERED',4999.00,'BEIJING','北京市朝阳区***号','顺丰速运','SF202606150001','DELIVERED','2026-06-18 18:00+08','2026-06-15 09:00+08','2026-06-18 16:00+08'),
('O-U001-004','202608050001','U001','DELIVERED',2999.00,'BEIJING','北京市朝阳区***号','顺丰速运','SF202608050001','DELIVERED','2026-08-08 18:00+08','2026-08-05 09:00+08','2026-08-08 16:00+08'),
('O-U002-001','202608100001','U002','DELIVERED',2499.00,'SHANGHAI','上海市徐汇区***号','顺丰速运','SF202608100001','DELIVERED','2026-08-13 18:00+08','2026-08-10 09:00+08','2026-08-13 16:00+08'),
('O-U002-002','202608200001','U002','DELIVERED',899.00,'SHANGHAI','上海市徐汇区***号','顺丰速运','SF202608200001','DELIVERED','2026-08-23 18:00+08','2026-08-20 09:00+08','2026-08-23 16:00+08'),
('O-U002-003','202607050001','U002','DELIVERED',6999.00,'SHANGHAI','上海市徐汇区***号','顺丰速运','SF202607050001','DELIVERED','2026-07-08 18:00+08','2026-07-05 09:00+08','2026-07-08 16:00+08'),
('O-U002-004','202605030001','U002','DELIVERED',12999.00,'SHANGHAI','上海市徐汇区***号','顺丰速运','SF202605030001','DELIVERED','2026-05-06 18:00+08','2026-05-03 09:00+08','2026-05-06 16:00+08'),
('O-U003-001','202608120001','U003','DELIVERED',2499.00,'GUANGZHOU','广州市天河区***号','顺丰速运','SF202608120001','DELIVERED','2026-08-15 18:00+08','2026-08-12 09:00+08','2026-08-15 16:00+08'),
('O-U003-002','202608180001','U003','DELIVERED',12999.00,'GUANGZHOU','广州市天河区***号','顺丰速运','SF202608180001','DELIVERED','2026-08-21 18:00+08','2026-08-18 09:00+08','2026-08-21 16:00+08'),
('O-U004-001','202608010004','U004','DELIVERED',699.00,'CN_DEFAULT','验收地址***','顺丰速运','SF202608010004','DELIVERED','2026-08-04 18:00+08','2026-08-01 00:00+08','2026-08-04 16:00+08'),
('O-U004-002','202609010004','U004','DELIVERED',1299.00,'CN_DEFAULT','验收地址***','顺丰速运','SF202609010004','DELIVERED','2026-09-04 18:00+08','2026-09-01 00:00+08','2026-09-04 16:00+08');

INSERT INTO t_order_item
(id,order_id,product_id,product_name_snapshot,model_snapshot,price,quantity,device_sn) VALUES
('OI-U001-001','O-U001-001','P-PHONE-001','HUAWEI Mate 80 Pro','Mate 80 Pro',5999.00,1,NULL),
('OI-U001-002','O-U001-002','P-LAPTOP-001','HUAWEI MateBook X Pro 2026','MateBook X Pro 2026',12999.00,1,'ABC001'),
('OI-U001-003','O-U001-003','P-TABLET-001','HUAWEI MatePad Pro 13.2','MatePad Pro 13.2',4999.00,1,'ABC002'),
('OI-U001-004','O-U001-004','P-PHONE-003','HUAWEI nova 14','nova 14',2999.00,1,NULL),
('OI-U002-001','O-U002-001','P-WATCH-001','HUAWEI WATCH GT 6','WATCH GT 6',2499.00,1,'WATCH-SN-002-GT'),
('OI-U002-002','O-U002-002','P-WATCH-002','HUAWEI WATCH Fit 4','WATCH Fit 4',899.00,1,'WATCH-SN-002-FIT'),
('OI-U002-003','O-U002-003','P-LAPTOP-002','HUAWEI MateBook 14 2026','MateBook 14 2026',6999.00,1,'LAP-SN-002-14'),
('OI-U002-004','O-U002-004','P-LAPTOP-001','HUAWEI MateBook X Pro 2026','MateBook X Pro 2026',12999.00,1,'LAP-SN-002-XPRO'),
('OI-U003-001','O-U003-001','P-WATCH-001','HUAWEI WATCH GT 6','WATCH GT 6',2499.00,1,'WATCH-SN-003'),
('OI-U003-002','O-U003-002','P-LAPTOP-001','HUAWEI MateBook X Pro 2026','MateBook X Pro 2026',12999.00,1,'LAP-SN-003'),
('OI-U004-001','O-U004-001','P-ROUTER-001','HUAWEI WiFi 7 路由器','WiFi 7',699.00,1,NULL),
('OI-U004-002','O-U004-002','P-ROUTER-002','HUAWEI Q6 Mesh','Q6 Mesh',1299.00,1,NULL);

INSERT INTO t_device
(id,sn,user_id,product_id,order_item_id,purchase_date,activation_date,warranty_start,warranty_end,warranty_type,device_status) VALUES
('D-U001-001','ABC001','U001','P-LAPTOP-001','OI-U001-002','2026-07-10','2026-07-14','2026-07-10','2027-07-09','STANDARD','REPAIRING'),
('D-U001-002','ABC002','U001','P-TABLET-001','OI-U001-003','2026-06-15','2026-06-19','2026-06-15','2027-06-14','STANDARD','ACTIVE'),
('D-U002-001','WATCH-SN-002-GT','U002','P-WATCH-001','OI-U002-001','2026-08-10','2026-08-14','2026-08-10','2027-08-09','STANDARD','REPAIRING'),
('D-U002-002','WATCH-SN-002-FIT','U002','P-WATCH-002','OI-U002-002','2026-08-20','2026-08-24','2026-08-20','2027-08-19','STANDARD','ACTIVE'),
('D-U002-003','LAP-SN-002-14','U002','P-LAPTOP-002','OI-U002-003','2026-07-05','2026-07-09','2026-07-05','2027-07-04','STANDARD','ACTIVE'),
('D-U002-004','LAP-SN-002-XPRO','U002','P-LAPTOP-001','OI-U002-004','2026-05-03','2026-05-07','2026-05-03','2027-05-02','STANDARD','ACTIVE'),
('D-U003-001','WATCH-SN-003','U003','P-WATCH-001','OI-U003-001','2026-08-12','2026-08-16','2026-08-12','2027-08-11','STANDARD','ACTIVE'),
('D-U003-002','LAP-SN-003','U003','P-LAPTOP-001','OI-U003-002','2026-08-18','2026-08-22','2026-08-18','2027-08-17','STANDARD','ACTIVE'),
('D-U004-001','ACCEPT-EXPIRED-001','U004','P-PHONE-002',NULL,'2026-05-10','2026-05-11','2026-05-10','2026-08-09','REFURBISHED_90DAYS','ACTIVE'),
('D-U004-002','ACCEPT-UNKNOWN-001','U004','P-HEADPHONE-001',NULL,'2026-05-10','2026-05-11',NULL,NULL,'UNKNOWN','ACTIVE');

-- Every order has fixed tracking facts and events; no random logistics synthesis.
INSERT INTO t_logistics_event (id,order_id,event_time,location,description)
SELECT 'LE-' || o.id || '-1',o.id,o.created_at + interval '1 hour',
       CASE o.receiver_region WHEN 'BEIJING' THEN '北京集散中心'
            WHEN 'SHANGHAI' THEN '上海集散中心'
            WHEN 'GUANGZHOU' THEN '广州集散中心' ELSE '全国 Mock 集散中心' END,
       '快件已揽收'
FROM t_order o;
INSERT INTO t_logistics_event (id,order_id,event_time,location,description)
SELECT 'LE-' || o.id || '-2',o.id,
       CASE WHEN o.status='SHIPPED' THEN o.created_at + interval '2 hours'
            ELSE o.created_at + interval '2 days' END,
       CASE o.receiver_region WHEN 'BEIJING' THEN '北京配送站'
            WHEN 'SHANGHAI' THEN '上海配送站'
            WHEN 'GUANGZHOU' THEN '广州配送站' ELSE '全国 Mock 配送站' END,
       CASE WHEN o.status='SHIPPED' THEN '运输中' ELSE '派送完成' END
FROM t_order o;

INSERT INTO t_coupon
(id,coupon_name,coupon_type,min_amount,discount_amount,start_time,end_time,applicable_category,status) VALUES
('C001','满3000减300','AMOUNT_OFF',3000.00,300.00,'2026-08-01 00:00+08','2026-09-30 00:00+08','laptop','ACTIVE'),
('C002','满5000减600','AMOUNT_OFF',5000.00,600.00,'2026-08-01 00:00+08','2026-09-30 00:00+08','laptop','ACTIVE'),
('C003','暑期满2000减200','AMOUNT_OFF',2000.00,200.00,'2026-07-01 00:00+08','2026-09-01 00:00+08',NULL,'INACTIVE'),
('C004','手机满2000减100','AMOUNT_OFF',2000.00,100.00,'2026-08-01 00:00+08','2026-09-30 00:00+08','phone','ACTIVE');

INSERT INTO t_user_coupon (id,user_id,coupon_id,status,claimed_at,used_at) VALUES
('UC-U001-C001','U001','C001','UNUSED','2026-08-02 09:00+08',NULL),
('UC-U001-C003','U001','C003','UNUSED','2026-07-05 09:00+08',NULL),
('UC-U002-C001','U002','C001','UNUSED','2026-08-02 09:00+08',NULL),
('UC-U002-C002','U002','C002','UNUSED','2026-08-02 09:00+08',NULL),
('UC-U003-C001','U003','C001','UNUSED','2026-08-02 09:00+08',NULL),
('UC-U003-C004','U003','C004','USED','2026-08-02 09:00+08','2026-08-20 09:00+08');

INSERT INTO t_repair_order
(id,service_no,user_id,device_sn,issue_description,status,current_step,contact_phone,created_at,estimated_complete_time,completed_at) VALUES
('R-U001-001','WX202609001','U001','ABC001','屏幕显示异常','REPAIRING','更换屏幕中','13800000001','2026-09-08 09:00+08','2026-09-18 18:00+08',NULL),
('R-U001-002','WX202608002','U001','ABC002','充电故障','COMPLETED','已完成维修','13800000001','2026-08-21 09:00+08','2026-08-30 18:00+08','2026-08-29 16:00+08'),
('R-U002-001','WX202608003','U002','WATCH-SN-002-GT','表盘触控异常','INSPECTING','检测中','13900000002','2026-08-22 09:00+08','2026-09-18 18:00+08',NULL),
('R-U002-002','WX202607004','U002','LAP-SN-002-14','键盘失灵','CLOSED','服务已关闭','13900000002','2026-07-15 09:00+08','2026-07-25 18:00+08','2026-07-24 16:00+08');

INSERT INTO t_repair_progress_event (id,repair_order_id,event_time,status,description) VALUES
('RE-U001-001-A','R-U001-001','2026-09-08 09:00+08','RECEIVED','服务单已受理'),
('RE-U001-001-B','R-U001-001','2026-09-09 10:00+08','INSPECTING','设备检测中'),
('RE-U001-001-C','R-U001-001','2026-09-10 11:00+08','REPAIRING','屏幕维修中'),
('RE-U001-002-A','R-U001-002','2026-08-21 09:00+08','RECEIVED','服务单已受理'),
('RE-U001-002-B','R-U001-002','2026-08-25 10:00+08','REPAIRING','充电模块维修中'),
('RE-U001-002-C','R-U001-002','2026-08-29 16:00+08','COMPLETED','维修完成'),
('RE-U002-001-A','R-U002-001','2026-08-22 09:00+08','RECEIVED','服务单已受理'),
('RE-U002-001-B','R-U002-001','2026-08-23 10:00+08','INSPECTING','手表检测中'),
('RE-U002-002-A','R-U002-002','2026-07-15 09:00+08','RECEIVED','服务单已受理'),
('RE-U002-002-B','R-U002-002','2026-07-20 10:00+08','COMPLETED','维修完成'),
('RE-U002-002-C','R-U002-002','2026-07-24 16:00+08','CLOSED','服务已关闭');

INSERT INTO t_spare_part_price
(id,product_id,part_code,part_name,part_price,labor_fee,region,price_note,effective_at,updated_at) VALUES
('SP-XPRO-SCREEN-OLD','P-LAPTOP-001','screen','屏幕组件',2799.00,300.00,'CN_DEFAULT','历史版本','2026-08-01 00:00+08','2026-08-01 00:00+08'),
('SP-XPRO-SCREEN','P-LAPTOP-001','screen','屏幕组件',2999.00,300.00,'CN_DEFAULT','以实际检测结果为准','2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-XPRO-BATTERY','P-LAPTOP-001','battery','电池组件',799.00,200.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-XPRO-SCREEN-BJ','P-LAPTOP-001','screen','屏幕组件',3099.00,300.00,'BEIJING','北京 Mock 地区价','2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-MB14-SCREEN','P-LAPTOP-002','screen','屏幕组件',1599.00,200.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-MBD16-SCREEN','P-LAPTOP-003','screen','屏幕组件',999.00,200.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-MBX-SCREEN','P-LAPTOP-004','screen','屏幕组件',2299.00,250.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-WGT-SCREEN','P-WATCH-001','screen','手表屏幕',499.00,100.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-WFIT-SCREEN','P-WATCH-002','screen','手表屏幕',299.00,100.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-W5-SCREEN','P-WATCH-003','screen','手表屏幕',699.00,100.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08'),
('SP-MATE80-SCREEN','P-PHONE-001','screen','手机屏幕',899.00,150.00,'CN_DEFAULT',NULL,'2026-09-01 00:00+08','2026-09-01 00:00+08');

COMMIT;
