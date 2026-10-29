package com.gtnewhorizons.horizonqa.examples.tests;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.HorizonQAProperties;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** Exercises default mob isolation in the real integrated world */
@GameTestHolder(value = "horizonqaexamples", clientOnly = true)
public final class ClientEnvironmentTests {

    private ClientEnvironmentTests() {}

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void mobsAreDisabledByDefault(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("sound override did not leak from the previous scenario", client -> {
                float master = Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.MASTER);
                if ((master > 0) != HorizonQAProperties.clientSoundEnabled()) {
                    throw new AssertionError("Teardown did not restore the run's sound default: " + master);
                }
            })
            .server("default world rejects animals and monsters", () -> {
                assertRejected(helper, new EntityCow(helper.getWorld()));
                assertRejected(helper, new EntityZombie(helper.getWorld()));
                if (helper.getWorld()
                    .getGameRules()
                    .getGameRuleBooleanValue("doMobSpawning")) {
                    throw new AssertionError("Natural mob spawning is enabled by default");
                }
                assertNoLiveMobs(helper);
                TestPos pos = helper.absolute("marker");
                EntityItem item = new EntityItem(
                    helper.getWorld(),
                    pos.x(),
                    pos.y() + 2,
                    pos.z(),
                    new ItemStack(Items.stick));
                if (!helper.getWorld()
                    .spawnEntityInWorld(item)) throw new AssertionError("Mob isolation rejected an item");
                helper.afterTest(item::setDead);
            })
            .succeed();
    }

    @GameTest(template = "client_smoke", timeoutTicks = 200)
    public static void environmentOverridesAreScoped(GameTestHelper helper) {
        EntityCow cow = new EntityCow(helper.getWorld());
        TestPos pos = helper.absolute("marker");
        cow.setPosition(pos.x() + 0.5, pos.y() + 2, pos.z() + 0.5);
        ClientTest.scenario(helper)
            .mobs(true)
            .server("enabled environment admits an animal", () -> {
                if (!helper.getWorld()
                    .getGameRules()
                    .getGameRuleBooleanValue("doMobSpawning")) {
                    throw new AssertionError("Natural mob spawning was not enabled");
                }
                if (!helper.getWorld()
                    .spawnEntityInWorld(cow)) throw new AssertionError("Enabled environment rejected a cow");
            })
            .sound(true)
            .client("enabled sound uses configured master level", client -> {
                if (Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.MASTER) <= 0) {
                    throw new AssertionError("Scenario did not enable configured sound");
                }
            })
            .sound(false)
            .client("disabled sound silences every category", client -> {
                for (SoundCategory category : SoundCategory.values()) {
                    if (Minecraft.getMinecraft().gameSettings.getSoundLevel(category) != 0) {
                        throw new AssertionError("Scenario did not silence " + category);
                    }
                }
            })
            .mobs(false)
            .server("disabling removes existing mobs and rejects new ones", () -> {
                if (!cow.isDead) throw new AssertionError("Existing cow survived disabling mobs");
                assertRejected(helper, new EntityZombie(helper.getWorld()));
            })
            .sound(true)
            .mobs(true)
            .server("leave a live mob for session cleanup", () -> {
                if (!helper.getWorld()
                    .spawnEntityInWorld(new EntityCow(helper.getWorld()))) {
                    throw new AssertionError("Enabled environment rejected cleanup fixture");
                }
            })
            .succeed();
        helper.afterTest(() -> {
            if (helper.getWorld()
                .getGameRules()
                .getGameRuleBooleanValue("doMobSpawning")) {
                throw new AssertionError("Teardown leaked the mob spawning override");
            }
            assertNoLiveMobs(helper);
        });
    }

    private static void assertNoLiveMobs(GameTestHelper helper) {
        for (Object entity : helper.getWorld().loadedEntityList) {
            if (entity instanceof EntityLiving && !((EntityLiving) entity).isDead) {
                throw new AssertionError("Live mob remained in default environment: " + entity);
            }
        }
    }

    private static void assertRejected(GameTestHelper helper, EntityLiving mob) {
        TestPos pos = helper.absolute("marker");
        mob.setPosition(pos.x() + 0.5, pos.y() + 2, pos.z() + 0.5);
        if (helper.getWorld()
            .spawnEntityInWorld(mob)) {
            mob.setDead();
            throw new AssertionError(
                "Default client environment admitted " + mob.getClass()
                    .getSimpleName());
        }
    }
}
