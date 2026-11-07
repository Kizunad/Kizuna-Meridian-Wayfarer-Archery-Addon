package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Hand;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.kizuna.meridian.human.fabric.WayfarerTestPlayer.AMMUNITION;

/** 正式目录经过学习、真实 tick、分段费用与原版箭工厂的整合回归。 */
public final class WayfarerGameTest {
    private static final int TIMEOUT_TICKS = 180;
    private static final int FULL_DRAW_PAID_TICK = 50;
    private static final int FULL_DRAW_RELEASED_TICK = 80;
    private static final int FULL_DRAW_FINISHED_TICK = 125;
    private static final int REPEATER_FIRST_PAID_TICK = 26;
    private static final int REPEATER_RELEASED_TICK = 68;
    private static final int REPEATER_SHOTS = 5;
    private static final int DRY_FIRST_PAID_TICK = 25;
    private static final int DRY_SECOND_SEGMENT_TICK = 145;
    private static final int DRY_TRAINING_TIMEOUT_TICKS = 700;
    private static final int DRY_LAST_PAID_TICK = 505;
    private static final int DRY_RECOVERY_FINISHED_TICK = 660;
    private static final int DRY_SEGMENTS = 5;
    private static final int ARM_MUSCLES = 6;
    private static final double DRY_SEGMENT_JOULES = 270;
    private static final double DRY_TOTAL_JOULES = 1350;
    private static final double MAXIMUM_TRAINING_GROWTH = 0.5;
    private static final double EPSILON = 0.000001;

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void fullDrawPaysBeforeArrowAndFinishes(GameTestHelper context) {
        // 起手先预检，拉弦开始付费，撒放才扣弹药，收势清理动作。
        var fixture = new WayfarerTestPlayer(context);
        fixture.equip(Items.BOW, InteractionHand.MAIN_HAND);
        context.assertTrue(fixture.cast("full_draw").getPhase() == CastPhase.WARMUP, "正式满月起手失败");
        context.assertTrue(fixture.armFatigue() == 0, "起手提前扣了未来拉弦费用");
        context.runAfterDelay(FULL_DRAW_PAID_TICK, () -> {
            context.assertTrue(fixture.progress().getActiveCast().getPaidWorkSequencesCount() == 1,
                    "拉弦开始后未记录已付段");
            context.assertTrue(fixture.armFatigue() > 0 && fixture.arrows().isEmpty(), "做功未早于放箭");
            context.assertTrue(fixture.player.getOffhandItem().getCount() == AMMUNITION, "拉弦时提前扣箭");
        });
        context.runAfterDelay(FULL_DRAW_RELEASED_TICK, () -> {
            context.assertTrue(fixture.arrows().size() == 1, "满月未通过真实宿主生成一支箭");
            context.assertTrue(fixture.player.getOffhandItem().getCount() == AMMUNITION - 1,
                    "满月弹药扣除错误");
        });
        context.runAfterDelay(FULL_DRAW_FINISHED_TICK, () -> {
            try (fixture) {
                context.assertTrue(!fixture.progress().hasActiveCast(), "满月收势未完成");
                context.assertTrue(fixture.body.snapshot().actions().isEmpty(), "收势遗留动作占用");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void offhandRepeaterPaysFiveSegmentsAndShootsFiveArrows(GameTestHelper context) {
        // 副手持弩绑定另一臂，连续分段不能整式预扣或漏掉后续发射。
        var fixture = new WayfarerTestPlayer(context);
        fixture.equip(Items.CROSSBOW, InteractionHand.OFF_HAND);
        var outcome = fixture.cast("repeater");
        context.assertTrue(outcome.getPhase() == CastPhase.WARMUP, "副手连弩起手失败：" + outcome);
        context.assertTrue(fixture.progress().getActiveCast().getSelectedHand() == Hand.OFF_HAND,
                "未按真实持物选择副手");
        context.runAfterDelay(REPEATER_FIRST_PAID_TICK, () -> {
            context.assertTrue(fixture.progress().getActiveCast().getPaidWorkSequencesCount() == 1,
                    "第一拉提前收取后续段费用");
            context.assertTrue(fixture.arrows().isEmpty(), "连弩提前发射");
        });
        context.runAfterDelay(REPEATER_RELEASED_TICK, () -> {
            try (fixture) {
                context.assertTrue(fixture.progress().getActiveCast().getPaidWorkSequencesCount() == REPEATER_SHOTS,
                        "连弩分段未全部支付");
                context.assertTrue(fixture.player.getMainHandItem().getCount() == AMMUNITION - REPEATER_SHOTS,
                        "连弩弹药数错误");
                context.assertTrue(fixture.arrows().size() == REPEATER_SHOTS, "连弩实际箭数错误");
                context.succeed();
            }
        });
    }

    @GameTest(maxTicks = TIMEOUT_TICKS)
    public void dryDrawDisconnectKeepsPaidWorkAndDropsFutureSegments(GameTestHelper context) {
        // 空弓无需弹药；断线保留已付训练负荷，未开始段不能补扣。
        var fixture = new WayfarerTestPlayer(context);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        context.assertTrue(fixture.cast("dry_draw").getPhase() == CastPhase.WARMUP, "无弹药拉空弓被拒绝");
        context.runAfterDelay(DRY_FIRST_PAID_TICK, () -> {
            context.assertTrue(fixture.progress().getActiveCast().getPaidWorkSequencesCount() == 1,
                    "空弓首段未付或提前付了后段");
            context.assertTrue(fixture.armFatigue() > 0 && fixture.arrows().isEmpty(), "空弓费用或射击行为错误");
            assertArmTrainingLoad(context, fixture, DRY_SEGMENT_JOULES);
            fixture.close();
            var disconnected = fixture.body.snapshot();
            context.assertTrue(!fixture.progress().hasActiveCast(), "断线未取消空弓");
            assertArmTrainingLoad(context, fixture, DRY_SEGMENT_JOULES);
            context.runAfterDelay(DRY_SECOND_SEGMENT_TICK - DRY_FIRST_PAID_TICK, () -> {
                context.assertTrue(disconnected.equals(fixture.body.snapshot()), "断线后仍提交未来段费用");
                context.assertTrue(TechniqueFabric.service().observe(fixture.player.getUUID()).isEmpty(),
                        "断线后宿主仍可施放");
                context.succeed();
            });
        });
    }

    @GameTest(maxTicks = DRY_TRAINING_TIMEOUT_TICKS)
    public void dryDrawConsolidatesPaidTrainingAfterRecovery(GameTestHelper context) {
        // 五次拉弦分别付训练负荷，收势释放双臂后才巩固为永久耐力成长。
        var fixture = new WayfarerTestPlayer(context);
        var bow = new ItemStack(Items.BOW);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        context.assertTrue(fixture.cast("dry_draw").getPhase() == CastPhase.WARMUP, "空弓训练起手失败");
        context.runAfterDelay(DRY_LAST_PAID_TICK, () -> {
            context.assertTrue(fixture.progress().getActiveCast().getPaidWorkSequencesCount() == DRY_SEGMENTS,
                    "空弓未支付全部五段训练费用");
            assertArmTrainingLoad(context, fixture, DRY_TOTAL_JOULES);
        });
        context.runAfterDelay(DRY_RECOVERY_FINISHED_TICK, () -> {
            try (fixture) {
                context.assertTrue(!fixture.progress().hasActiveCast(), "空弓收势未完成");
                context.assertTrue(fixture.body.snapshot().actions().isEmpty(), "空弓收势遗留双臂占用");
                for (var tissue : fixture.armStates()) {
                    var tissueId = tissue.id();
                    double growth = tissue.trainingGrowth();
                    context.assertTrue(growth > 0 && growth <= MAXIMUM_TRAINING_GROWTH,
                            tissueId + "未巩固为合法耐力成长");
                    context.assertTrue(tissue.pendingTrainingLoad() < DRY_TOTAL_JOULES,
                            tissueId + "收势后训练负荷未巩固");
                }
                context.assertTrue(fixture.arrows().isEmpty(), "空弓训练生成了箭");
                context.assertTrue(bow.getDamageValue() == 0, "空弓训练扣除了弓耐久");
                context.succeed();
            }
        });
    }

    private static void assertArmTrainingLoad(GameTestHelper context, WayfarerTestPlayer fixture,
                                             double expectedJoules) {
        // 已付负荷按肌肉保存，未推进休息时间时不能提前巩固或重复计算。
        var tissues = fixture.armStates();
        context.assertTrue(tissues.size() == ARM_MUSCLES, "空弓训练未覆盖六块手臂肌肉");
        for (var tissue : tissues) {
            var tissueId = tissue.id();
            double pendingLoad = tissue.pendingTrainingLoad();
            context.assertTrue(Math.abs(pendingLoad - expectedJoules) < EPSILON,
                    tissueId + "已付训练负荷错误：" + pendingLoad);
            context.assertTrue(tissue.trainingGrowth() == 0, tissueId + "动作占用时提前巩固训练");
        }
    }
}
