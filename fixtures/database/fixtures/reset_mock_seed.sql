-- Draft-only reset for reloading the reviewed Seed. Explicit Mock tables, no CASCADE.
BEGIN;
TRUNCATE TABLE
  huawei_mall_mock.t_repair_progress_event,
  huawei_mall_mock.t_logistics_event,
  huawei_mall_mock.t_repair_order,
  huawei_mall_mock.t_user_coupon,
  huawei_mall_mock.t_spare_part_price,
  huawei_mall_mock.t_inventory,
  huawei_mall_mock.t_device,
  huawei_mall_mock.t_order_item,
  huawei_mall_mock.t_order,
  huawei_mall_mock.t_coupon,
  huawei_mall_mock.t_product,
  huawei_mall_mock.t_user;
COMMIT;
