package dev.kizuna.meridian.wayfarer.verification;

import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.*;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.Assert.*;

/** 正式目录的发现、整式费用守恒与发布边界回归。 */
public final class WayfarerCatalogTest {
    private static final String PREFIX = "meridian_wayfarer:";
    private static final int TECHNIQUES = 10;
    private static final int VOLUMES = 3;
    private static final int SINGLE_ARM_MUSCLES = 3;
    private static final int BOTH_ARM_MUSCLES = 6;
    private static final int DRY_DRAW_SEGMENTS = 5;
    private static final long CONVERGENCE_MODIFIER_MILLIS = 3600;
    private static final double EPSILON = 0.000001;
    private static final Map<String, Double> WORK_JOULES = Map.of(
            "full_draw", 325.0, "pearls", 150.0, "bow_strike", 380.0,
            "repeater", 500.0, "gale", 525.0, "kneeling", 330.0,
            "parthian", 685.0, "arrow_rain", 650.0, "convergence", 520.0, "dry_draw", 8100.0);
    private static final Map<String, Integer> SHOTS = Map.of(
            "full_draw", 1, "pearls", 3, "bow_strike", 1, "repeater", 5, "gale", 6,
            "kneeling", 3, "parthian", 1, "arrow_rain", 5, "convergence", 4, "dry_draw", 0);

    @Test
    public void realHumanCatalogRegistersAllVolumesAndDefinitions() throws IOException {
        // 使用真实 Human 标签和功法注册表，缺字段与非法效果均由公共校验器拒绝。
        TechniqueRegistry registry = registry();
        assertEquals(1, registry.books().size());
        assertEquals(VOLUMES, registry.volumes().size());
        assertEquals(TECHNIQUES, registry.definitions().size());
        var lower = registry.findVolume(PREFIX + "bow_lower").orElseThrow();
        assertEquals(PREFIX + "bow_upper", lower.getPrerequisiteVolumeIds(0));
    }

    @Test
    public void segmentedWorkPreservesWholeTechniqueBudgetsAndShotCounts() throws IOException {
        // 各肌肉各付一次，合计焦耳必须与设计总功率乘时长一致，不能把六肌费用重复六倍。
        TechniqueRegistry registry = registry();
        for (var expected : WORK_JOULES.entrySet()) {
            TechniqueDefinition definition = registry.findLatest(PREFIX + expected.getKey()).orElseThrow();
            assertEquals(0, definition.getCost().getWorkCount());
            double joules = 0;
            for (TechniqueEffect effect : definition.getEffectsList()) {
                if (effect.hasWork()) {
                    WorkCost work = effect.getWork();
                    var slot = definition.getSlotsList().stream()
                            .filter(value -> value.getId().equals(work.getSlotId())).findFirst().orElseThrow();
                    joules += slot.getMinimumCount() * work.getPower().getValue() * work.getDuration().getValue();
                }
            }
            assertEquals(expected.getKey(), expected.getValue(), joules, EPSILON);
            long shots = definition.getEffectsList().stream().filter(TechniqueEffect::hasLaunchProjectile).count();
            assertEquals(SHOTS.get(expected.getKey()).longValue(), shots);
        }
    }

    @Test
    public void singleArmSlotsUseRealAnatomicalCandidatesAndOtherHand() throws IOException {
        // 单臂式必须随握持手选另一臂，双臂式保持六肌而不假冒相对槽。
        for (TechniqueDefinition definition : registry().definitions()) {
            for (TechniqueSlot slot : definition.getSlotsList()) {
                if (slot.getId().equals("draw")) {
                    RelativeArmSlot arm = slot.getRelativeArm();
                    assertEquals(DrawStrengthArm.OTHER_ARM, arm.getArm());
                    assertEquals(SINGLE_ARM_MUSCLES, slot.getMinimumCount());
                    assertEquals(SINGLE_ARM_MUSCLES, arm.getLeftModuleIdsCount());
                    assertEquals(SINGLE_ARM_MUSCLES, arm.getRightModuleIdsCount());
                } else if (slot.getId().equals("arms")) {
                    assertEquals(BOTH_ARM_MUSCLES, slot.getMinimumCount());
                    assertFalse(slot.hasRelativeArm());
                }
            }
        }
    }

    @Test
    public void dryDrawAndConvergenceKeepTheirRestrictions() throws IOException {
        // 空弓的每次拉弦都积累训练负荷，天地同至仍只能承接并锁定目标。
        TechniqueRegistry registry = registry();
        TechniqueDefinition dry = registry.findLatest(PREFIX + "dry_draw").orElseThrow();
        assertFalse(dry.getRequireAmmunition());
        assertEquals(0, dry.getCost().getQiCount());
        assertEquals(0, dry.getMovementSpeedMultiplier(), 0);
        assertEquals(0, dry.getAiHints().getCategoryWeight().getValue(), 0);
        var trainingSegments = dry.getEffectsList().stream()
                .filter(TechniqueEffect::hasWork)
                .map(TechniqueEffect::getWork)
                .toList();
        assertEquals(DRY_DRAW_SEGMENTS, trainingSegments.size());
        assertTrue(trainingSegments.stream().allMatch(WorkCost::getTraining));
        TechniqueDefinition convergence = registry.findLatest(PREFIX + "convergence").orElseThrow();
        assertTrue(convergence.getFollowUp().getOnlyFollowUp());
        assertTrue(convergence.getLockTarget());
        var modifiers = convergence.getEffectsList().stream().filter(TechniqueEffect::hasTissueModifier).toList();
        assertEquals(1, modifiers.size());
        TechniqueEffect modifier = modifiers.getFirst();
        assertEquals(CONVERGENCE_MODIFIER_MILLIS, modifier.getAtMillis());
        assertEquals(ModifierActivation.AT_EFFECT_TIME, modifier.getTissueModifier().getActivation());
    }

    @Test
    public void releaseJarContainsCatalogAndNoExecutableClasses() throws IOException {
        // 校验真正发行 JAR，防止资源只在测试类路径可见或把验证宿主打入正式包。
        try (var jar = new JarFile(System.getProperty("wayfarerArchery.fabricJar"))) {
            String path = WayfarerCatalog.RESOURCE.substring(1);
            assertNotNull(jar.getJarEntry(path));
            assertTrue(jar.stream().noneMatch(entry -> entry.getName().endsWith(".class")));
            try (var source = WayfarerCatalog.class.getResourceAsStream(WayfarerCatalog.RESOURCE)) {
                assertNotNull(source);
                assertArrayEquals(source.readAllBytes(), jar.getInputStream(jar.getJarEntry(path)).readAllBytes());
            }
        }
    }

    private static TechniqueRegistry registry() throws IOException {
        // 注册与加载共用发行 textproto，不在测试里复制一份功法定义。
        return WayfarerCatalog.registry(WayfarerCatalog.load());
    }
}
