package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueScrollDrops;
import dev.kizuna.meridian.technique.fabric.TechniqueScrollItem;
import dev.kizuna.meridian.technique.fabric.TechniqueScrollItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 行矢的生存来源：玩家亲手杀死会某一卷的掠夺者，才按目录概率掉这一卷。
 *
 * <p>掠夺者走 Threatening 的正式生成命令，按档位照学卷表学卷；掉落走原版掠夺者掉落表。</p>
 */
public final class WayfarerScrollDropGameTest {
    private static final String CROSSBOW = WayfarerTestPlayer.PREFIX + "crossbow";
    private static final String BOW_UPPER = WayfarerTestPlayer.PREFIX + "bow_upper";
    private static final List<String> VOLUMES = List.of(CROSSBOW, BOW_UPPER, WayfarerTestPlayer.PREFIX + "bow_lower");
    private static final int TIMEOUT = 100;
    private static final int BIRTH_TICKS = 2;
    private static final int LOOT_ROLLS = 200;
    private static final int LEAST_DROPS = 25;
    private static final int MOST_DROPS = 75;
    private static final int LAST_HURT_TICKS = 100;

    @GameTest(maxTicks = TIMEOUT)
    public void pillagersDropOnlyTheVolumesTheyKnow(GameTestHelper context) {
        // 真炁掠夺者会弩卷和弓上卷，游炁只会弩卷，凡人一卷不会；玩家打死时会的卷各掉约四分之一，其余不掉。
        var fixture = new WayfarerTestPlayer(context);
        ServerLevel level = context.getLevel();
        Mob trueQi = spawn(context, "true_qi", new BlockPos(1, 2, 1));
        Mob wandering = spawn(context, "wandering_qi", new BlockPos(3, 2, 1));
        Mob mortal = spawn(context, "mortal", new BlockPos(5, 2, 1));
        context.runAfterDelay(BIRTH_TICKS, () -> {
            try (fixture) {
                context.assertTrue(knows(trueQi, CROSSBOW) && knows(trueQi, BOW_UPPER), "真炁掠夺者没学会弩卷和弓上卷");
                context.assertTrue(knows(wandering, CROSSBOW) && !knows(wandering, BOW_UPPER), "游炁掠夺者学卷不符");
                context.assertTrue(VOLUMES.stream().noneMatch(volume -> knows(mortal, volume)), "凡人掠夺者也学了卷");
                DamageSource byPlayer = level.damageSources().playerAttack(fixture.player);
                for (Mob pillager : List.of(trueQi, wandering, mortal)) {
                    Map<String, Integer> drops = roll(level, pillager, byPlayer, fixture.player);
                    System.out.println("[行矢掉落] 玩家击杀 " + LOOT_ROLLS + " 次：" + drops);
                    assertDropsFollowKnowledge(context, pillager, drops);
                }
                DamageSource byMob = level.damageSources().mobAttack(mortal);
                context.assertTrue(total(roll(level, trueQi, byMob, null)) == 0, "不是玩家打死的掠夺者也掉卷");
                context.succeed();
            }
        });
    }

    private static Mob spawn(GameTestHelper context, String tier, BlockPos relative) {
        // 走 Threatening 的正式生成命令，按档位生出有身体、照学卷表学过卷的掠夺者；关掉 AI 免得它放箭。
        ServerLevel level = context.getLevel();
        Vec3 position = context.absoluteVec(Vec3.atBottomCenterOf(relative));
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        String command = String.format(Locale.ROOT, "meridian humanoid spawn minecraft:pillager %s %.2f %.2f %.2f",
                tier, position.x, position.y, position.z);
        server.getCommands().performPrefixedCommand(source, command);
        List<Mob> spawned = level.getEntitiesOfClass(Mob.class, new AABB(BlockPos.containing(position)),
                mob -> mob.getType() == EntityTypes.PILLAGER);
        if (spawned.size() != 1) {
            throw new IllegalStateException("生成命令没有在原地生出一只掠夺者：" + command + " → " + spawned);
        }
        Mob pillager = spawned.getFirst();
        pillager.setNoAi(true);
        return pillager;
    }

    private static void assertDropsFollowKnowledge(GameTestHelper context, Mob pillager, Map<String, Integer> drops) {
        // 会的卷掉落次数落在 25% 的正负四个标准差里，不会的卷一次不掉；弓下卷只有一成真炁掠夺者会，两种情形都算对。
        for (String volume : VOLUMES) {
            int count = drops.get(volume);
            if (knows(pillager, volume)) {
                context.assertTrue(count >= LEAST_DROPS && count <= MOST_DROPS, volume + " 掉落偏离 25%：" + count);
            } else {
                context.assertTrue(count == 0, "不会 " + volume + " 的掠夺者也掉了这一卷：" + count);
            }
        }
    }

    private static boolean knows(Mob pillager, String volume) {
        // 和掉落条件同一判定：功法服务里学会了本卷全部式。
        return new TechniqueScrollDrops.KnowsVolume(volume).knows(pillager);
    }

    private static Map<String, Integer> roll(ServerLevel level, Mob mob, DamageSource source, ServerPlayer killer) {
        // 用原版掉落入口抽 LOOT_ROLLS 次，按卷数掉出来的卷轴；killer 为空算非玩家击杀。
        boolean playerKilled = killer != null;
        if (playerKilled) {
            mob.setLastHurtByPlayer(killer, LAST_HURT_TICKS);
        }
        ResourceKey<LootTable> table = EntityTypes.PILLAGER.getDefaultLootTable().orElseThrow();
        List<ItemStack> drops = new ArrayList<>();
        for (int roll = 0; roll < LOOT_ROLLS; roll++) {
            mob.dropFromLootTable(level, source, playerKilled, table, drops::add);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String volume : VOLUMES) {
            TechniqueScrollItem scroll = scrollItem(volume);
            counts.put(volume, drops.stream().filter(stack -> stack.is(scroll)).mapToInt(ItemStack::getCount).sum());
        }
        return counts;
    }

    private static TechniqueScrollItem scrollItem(String volume) {
        // 功法库按卷生成的卷轴物品。
        TechniqueScrollItem item = TechniqueScrollItems.scrolls().get(Identifier.parse(volume + "_scroll"));
        if (item == null) {
            throw new IllegalStateException("没有卷轴物品：" + volume);
        }
        return item;
    }

    private static int total(Map<String, Integer> counts) {
        // 三卷掉落合计。
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
