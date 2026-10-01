package com.guildinvitefix.testing.scenarios;

import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.RunConfig;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the documented GUI-suite selection contract.
 *
 * <p>{@code RunConfig} resolves {@code mod:<id>} against the scenario class's
 * <em>package</em> ({@code classFQN.contains(".<id>.")}), not against the
 * Fabric mod id. While the scenarios lived under {@code com.ginv.*}, the
 * README command {@code -PldTest=mod:guildinvitefix} matched an empty queue
 * and the run aborted before the first screenshot was ever taken — which is
 * exactly the kind of silent, green-looking failure this test exists to
 * prevent.
 */
class ScenarioSelectionTest {

    /** The command README.md tells people to run. Keep in sync. */
    private static final String DOCUMENTED_MOD_SELECTION = "mod:guildinvitefix";
    private static final String DOCUMENTED_GROUP_SELECTION = "group:guildinvitefix";

    private static final List<Class<? extends UIScenario>> SCENARIOS = List.of(
            MenuOpenRegressionScenario.class,
            ScreenTabsStructureScenario.class,
            ControlFlowMockScenario.class,
            LevelQueueMockScenario.class,
            ScalePresetScenario.class,
            PopoutStabilityRedockScenario.class);

    @Test
    void documentedSelectionsMatchEveryScenario() throws Exception {
        for (var cls : SCENARIOS) {
            var annotation = cls.getAnnotation(LDLRegisterClient.class);
            assertNotNull(annotation,
                    cls.getName() + " must keep @LDLRegisterClient — discovery is an annotation scan");

            var scenario = cls.getDeclaredConstructor().newInstance();
            var options = new ScenarioOptions();
            scenario.configure(options);

            assertTrue(RunConfig.selectionMatches(DOCUMENTED_MOD_SELECTION,
                            annotation.name(), annotation.group(), options.tags(), cls),
                    cls.getName() + " is not selected by '" + DOCUMENTED_MOD_SELECTION
                            + "' — mod: matches on a '.guildinvitefix.' package segment");
            assertTrue(RunConfig.selectionMatches(DOCUMENTED_GROUP_SELECTION,
                            annotation.name(), annotation.group(), options.tags(), cls),
                    cls.getName() + " is not selected by '" + DOCUMENTED_GROUP_SELECTION + "'");
        }
    }
}
