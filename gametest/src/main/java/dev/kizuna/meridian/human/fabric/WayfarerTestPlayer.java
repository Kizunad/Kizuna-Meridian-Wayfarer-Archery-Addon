package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.AnatomyState;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.fabric.MeridianChannels;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.DebugQi;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.DebugQiMode;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.FailureCode;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.testing.RecordingConnection;
import dev.kizuna.meridian.technique.fabric.testing.TestPlayers;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.*;
import dev.kizuna.meridian.technique.server.TechniqueState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 只替换网络传输，身体、学习、费用和世界效果使用正式宿主。 */
final class WayfarerTestPlayer implements AutoCloseable {
    static final String PREFIX = "meridian_wayfarer:";
    static final long MILLIS_PER_TICK = 50;
    static final int AMMUNITION = 16;
    private static final int CENTER = 4;
    private static final int HEIGHT = 2;
    private static final double ARROW_SEARCH_RADIUS = 64;
    final ServerPlayer player;
    final HumanPlayerBody body;
    private final RecordingConnection connection;

    WayfarerTestPlayer(GameTestHelper context) {
        // 登录建立真实 Human 认领，测试不另建身体存储或重新注册目录。
        player = TestPlayers.detached(context.getLevel(), "wayfarer_test");
        connection = new RecordingConnection(List.of(MeridianChannels.BODY_VIEW.type().id(),
                Identifier.parse("kizuna_players_animation:play"), Identifier.parse("kizuna_players_animation:stop")));
        TestPlayers.logIn(player, connection);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true /* noGravity */);
        Vec3 position = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(CENTER, HEIGHT, CENTER)));
        player.snapTo(position.x, position.y, position.z, 0, 0);
        body = HumanTechniqueHost.state().find(player.getUUID());
        long nowMillis = player.level().getServer().overworld().getGameTime() * MILLIS_PER_TICK;
        context.assertTrue(body.completeOpening(nowMillis, "all") == FailureCode.FAILURE_CODE_UNSPECIFIED,
                "可信开脉失败");
        DebugQi fill = DebugQi.newBuilder().setMode(DebugQiMode.FILL_LUMENS).build();
        context.assertTrue(body.debugQi(nowMillis, fill) == FailureCode.FAILURE_CODE_UNSPECIFIED,
                "可信填充真元失败");
    }

    void equip(Item weapon, InteractionHand hand) {
        // 弹药放另一手，覆盖真实物品发现、消耗和主副手选择。
        InteractionHand ammunitionHand = InteractionHand.OFF_HAND;
        if (hand == InteractionHand.OFF_HAND) {
            ammunitionHand = InteractionHand.MAIN_HAND;
        }
        player.setItemInHand(hand, new ItemStack(weapon));
        player.setItemInHand(ammunitionHand, new ItemStack(Items.ARROW, AMMUNITION));
    }

    CastOutcome cast(String name) {
        // 学习和施放都引用正式目录，测试不复制效果参数。
        var service = TechniqueFabric.service();
        var definition = service.registry().findLatest(PREFIX + name).orElseThrow();
        service.learn(player.getUUID(), definition);
        TechniqueReference reference = TechniqueReference.newBuilder()
                .setId(definition.getId())
                .setVersion(definition.getVersion())
                .build();
        CastRequest request = CastRequest.newBuilder()
                .setBodyId(player.getStringUUID())
                .setCastId("wayfarer_" + name)
                .setTechnique(reference)
                .build();
        return service.cast(player.getUUID(), request);
    }

    TechniqueProgress progress() {
        // 费用序号与动作来自 Framework 唯一权威快照。
        return TechniqueState.read(body.snapshot());
    }

    double armFatigue() {
        // 按 Human 正式标签读取六肌疲劳，不以箭数反推付费。
        return armStates().stream().mapToDouble(AnatomyState.Tissue::fatigue).sum();
    }

    List<AnatomyState.Tissue> armStates() {
        // 疲劳与训练负荷来自同一组正式手臂肌肉状态。
        BodySnapshot snapshot = body.snapshot();
        var armIds = snapshot.structure().tissues().stream()
                .filter(tissue -> tissue.tags().stream().anyMatch(tag -> tag.value().equals("human:arm_muscle")))
                .map(tissue -> tissue.id()).toList();
        return snapshot.tissueStates().stream().filter(tissue -> armIds.contains(tissue.id())).toList();
    }

    List<AbstractArrow> arrows() {
        // 即便箭已碰到测试结构边界，仍以拥有者隔离实际生成的箭。
        return player.level().getEntitiesOfClass(AbstractArrow.class,
                player.getBoundingBox().inflate(ARROW_SEARCH_RADIUS), arrow -> arrow.getOwner() == player);
    }

    @Override
    public void close() {
        // 断线必须先取消功法；清除测试实体避免相邻测试误命中。
        arrows().forEach(AbstractArrow::discard);
        connection.disconnectFromClient();
    }
}
