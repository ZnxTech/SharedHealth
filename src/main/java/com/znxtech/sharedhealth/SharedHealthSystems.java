package com.znxtech.sharedhealth;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class SharedHealthSystems {

    private static final String MODIFIER_KEY = "Znxtech_SharedHealth_HealthModifier";
    private static final ConcurrentHashMap<UUID, Float> baseMaxHealths = new ConcurrentHashMap<UUID, Float>();
    private static final ConcurrentHashMap<UUID, Float> changeHealths = new ConcurrentHashMap<UUID, Float>();
    private static LockedFloat maxSharedHealth = new LockedFloat(0.0f);
    private static LockedFloat curSharedHealth = new LockedFloat(0.0f);

    private static void announceMaxHealthChange(float diff, String name) {
        if (diff < 0) {
            announceMaxHealthDecrease(diff, name);
        } else {
            announceMaxHealthIncrease(diff, name);
        }
    }

    private static void announceMaxHealthIncrease(float diff, String name) {
        Message message = Message.join(
            Message.raw("[").bold(true).color("#800000"),
            Message.raw("SharedHealth").bold(true).color("#cc0000"),
            Message.raw("]").bold(true).color("#800000"),
            Message.raw(": " + name + " Increased Shared Health by "),
            Message.raw(Float.toString(diff) + "HP").bold(true)
        );
        SharedHealth.LOGGER.atInfo().log(name + " Increased Shared Health by " + Float.toString(diff) + "HP");
        Universe.get().sendMessage(message);
        announceMaxHealthTotal();
    }

    private static void announceMaxHealthDecrease(float diff, String name) {
        Message message = Message.join(
            Message.raw("[").bold(true).color("#800000"),
            Message.raw("SharedHealth").bold(true).color("#cc0000"),
            Message.raw("]").bold(true).color("#800000"),
            Message.raw(": " + name + " Decreased Shared Health by "),
            Message.raw(Float.toString(Math.abs(diff)) + "HP").bold(true)
        );
        SharedHealth.LOGGER.atInfo().log(name + " Decreased Shared Health by " + Float.toString(Math.abs(diff)) + "HP");
        Universe.get().sendMessage(message);
        announceMaxHealthTotal();
    }

    private static void announceMaxHealthTotal() {
        Message message = Message.join(
            Message.raw("[").bold(true).color("#800000"),
            Message.raw("SharedHealth").bold(true).color("#cc0000"),
            Message.raw("]").bold(true).color("#800000"),
            Message.raw(": Total Shared Health is Now at "),
            Message.raw(Float.toString(curSharedHealth.get()) + "/" + Float.toString(maxSharedHealth.get()) + "HP").bold(true)
        );
        SharedHealth.LOGGER.atInfo().log("Total Shared Health is Now at " + Float.toString(curSharedHealth.get()) + "/" + Float.toString(maxSharedHealth.get()) + "HP");
        Universe.get().sendMessage(message);
    }

    private static void announceCurHealthHeal(float diff, String name) {
        Message message = Message.join(
            Message.raw("[").bold(true).color("#800000"),
            Message.raw("SharedHealth").bold(true).color("#cc0000"),
            Message.raw("]").bold(true).color("#800000"),
            Message.raw(": " + name + " Healed Shared Health by "),
            Message.raw(Float.toString(diff) + "HP!").bold(true)
        );
        SharedHealth.LOGGER.atInfo().log(name + "Healed Shared Health by " + Float.toString(diff) + "HP");
        Universe.get().sendMessage(message);
        announceMaxHealthTotal();
    }

    private static void announceCurHealthDamage(float diff, String name) {
        Message message = Message.join(
            Message.raw("[").bold(true).color("#800000"),
            Message.raw("SharedHealth").bold(true).color("#cc0000"),
            Message.raw("]").bold(true).color("#800000"),
            Message.raw(": Damage from " + name + " Damaged Shared Health by "),
            Message.raw(Float.toString(diff) + "HP").bold(true)
        );
        SharedHealth.LOGGER.atInfo().log("Damage from " + name + "Damaged Shared Health by " + Float.toString(diff) + "HP");
        Universe.get().sendMessage(message);
        announceMaxHealthTotal();
    }

    private static float getHealthMultiplier(EntityStatValue health) {
        Map<String, Modifier> modifiers = health.getModifiers();
        float multiplier = 1.0f;
        if (modifiers == null) return multiplier;

        for (Entry<String, Modifier> e : modifiers.entrySet()) {
            StaticModifier modifier = (StaticModifier)e.getValue();
            if (modifier.getTarget() == ModifierTarget.MAX && modifier.getCalculationType() == CalculationType.MULTIPLICATIVE) {
                multiplier *= modifier.getAmount();
            }
        }

        return multiplier;
    }

    private static float getBaseMaxHealth(EntityStatValue health) {
        StaticModifier modifier = (StaticModifier)health.getModifier(MODIFIER_KEY);
        float multiplier = getHealthMultiplier(health);
        return health.getMax() - (modifier != null ? modifier.getAmount() : 0.0f) * multiplier;
    }

    private static float getAddDiffNeeded(EntityStatValue health) {
        float multiplier = getHealthMultiplier(health);
        return (maxSharedHealth.get() - getBaseMaxHealth(health)) / multiplier;
    }

    private static void syncHealth() {
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> {
                for (PlayerRef playerRef : world.getPlayerRefs()) {
                    Ref<EntityStore> ref = playerRef.getReference();
                    EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                    EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());

                    StaticModifier modifier = new StaticModifier(
                        ModifierTarget.MAX, CalculationType.ADDITIVE, getAddDiffNeeded(health)
                    );

                    float change = curSharedHealth.get() - health.get();
                    changeHealths.put(playerRef.getUuid(), changeHealths.get(playerRef.getUuid()) + change);
                    stats.putModifier(DefaultEntityStatTypes.getHealth(), MODIFIER_KEY, modifier);
                    stats.setStatValue(DefaultEntityStatTypes.getHealth(), curSharedHealth.get());
                    stats.update();
                }
            });
        }
    }

    private static void syncHealthBesides(UUID uuid) {
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> {
                for (PlayerRef playerRef : world.getPlayerRefs()) {
                    if (playerRef.getUuid().equals(uuid)) continue;

                    Ref<EntityStore> ref = playerRef.getReference();
                    EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                    EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());

                    StaticModifier modifier = new StaticModifier(
                        ModifierTarget.MAX, CalculationType.ADDITIVE, getAddDiffNeeded(health)
                    );

                    float change = curSharedHealth.get() - health.get();
                    changeHealths.put(playerRef.getUuid(), changeHealths.get(playerRef.getUuid()) + change);
                    stats.putModifier(DefaultEntityStatTypes.getHealth(), MODIFIER_KEY, modifier);
                    stats.setStatValue(DefaultEntityStatTypes.getHealth(), curSharedHealth.get());
                    stats.update();
                }
            });
        }
    }

    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        float baseMaxHealth = health != null ? getBaseMaxHealth(health) : 0.0f;
        maxSharedHealth.change(baseMaxHealth);
        curSharedHealth.change(baseMaxHealth);
        baseMaxHealths.put(playerRef.getUuid(), baseMaxHealth);
        changeHealths.put(playerRef.getUuid(), health.get());
        syncHealth();
        announceMaxHealthIncrease(baseMaxHealth, playerRef.getUsername());
    }

    public static void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        float baseMaxHealth = baseMaxHealths.get(playerRef.getUuid());
        maxSharedHealth.change(-baseMaxHealth);
        if (curSharedHealth.get() > maxSharedHealth.get()) {
            changeHealths.put(playerRef.getUuid(), changeHealths.get(playerRef.getUuid()) - (curSharedHealth.get() - maxSharedHealth.get()));
            curSharedHealth = maxSharedHealth;
        }
        baseMaxHealths.remove(playerRef.getUuid());
        changeHealths.remove(playerRef.getUuid());
        syncHealthBesides(playerRef.getUuid());
        announceMaxHealthDecrease(baseMaxHealth, playerRef.getUsername());
    }

    public static class HealthChangeSystem extends EntityTickingSystem<EntityStore> {

        @Override
        public void tick(
            float dt, int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
                
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (!baseMaxHealths.containsKey(playerRef.getUuid())) return;

            EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());

            // Shared max health handling
            float baseHealth = getBaseMaxHealth(health);
            if (baseHealth != baseMaxHealths.get(playerRef.getUuid())) {
                float diff = baseHealth - baseMaxHealths.get(playerRef.getUuid());
                maxSharedHealth.change(diff);
                if (curSharedHealth.get() > maxSharedHealth.get()) {
                    changeHealths.put(playerRef.getUuid(), changeHealths.get(playerRef.getUuid()) - (curSharedHealth.get() - maxSharedHealth.get()));
                    curSharedHealth = maxSharedHealth;
                }
                baseMaxHealths.put(playerRef.getUuid(), baseHealth);
                syncHealthBesides(playerRef.getUuid());
                announceMaxHealthChange(diff, playerRef.getUsername());
            }

            // Shared healing handling
            if (health.get() != changeHealths.get(playerRef.getUuid())) {
                float diff = health.get() - changeHealths.get(playerRef.getUuid());
                curSharedHealth.change(diff);
                if (curSharedHealth.get() > maxSharedHealth.get()) 
                    curSharedHealth.set(maxSharedHealth.get());
                changeHealths.put(playerRef.getUuid(), health.get());
                syncHealthBesides(playerRef.getUuid());
                announceCurHealthHeal(diff, playerRef.getUsername());
            }
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                EntityStatMap.getComponentType()
            );
        }
    }

    public static class SharedDamage extends EntityEventSystem<EntityStore, Damage> {

        public SharedDamage() {
            super(Damage.class);
        }

        @Override
        public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage event) {
            
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            curSharedHealth.change(-event.getAmount());
            changeHealths.put(playerRef.getUuid(), changeHealths.get(playerRef.getUuid()) - event.getAmount());
            syncHealthBesides(playerRef.getUuid());
            if (curSharedHealth.get() < 0.0f)
                curSharedHealth.set(maxSharedHealth.get());
            announceCurHealthDamage(event.getAmount(), playerRef.getUsername());
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                EntityStatMap.getComponentType()
            );
        }
    }
}
