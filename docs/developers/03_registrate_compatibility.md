# Create Registrate compatibility and intrusive holders

Cancelling a registration in NeoForge 1.21.1 is simple when a mod uses `DeferredRegister`, and much harder when a mod uses Registrate (Create, TFMG, Create Big Cannons, Create Deco, Create Encased, and most of the Create ecosystem).

## Why naive cancellation crashes on Registrate mods

With NeoForge's standard `DeferredRegister`, item construction is lazy:

```java
DeferredHolder<Item, Item> MY_ITEM = ITEMS.register("my_item", () -> new Item(new Item.Properties()));
```

When `RegisterEventMixin` cancels `RegisterEvent#register` at `HEAD`, the supplier lambda never runs. No `Item` instance is ever constructed, and `DeferredHolder` simply resolves through the registry alias later.

Registrate works differently. Inside `Item#<init>` and `Block#<init>`, vanilla Minecraft creates an intrusive holder (`BuiltInRegistries.ITEM.createIntrusiveHolder(this)`) the moment `new Item(...)` runs.

During `RegisterEvent`, `AbstractRegistrate$Registration#register` does two steps in sequence:
1. Calls `creator.get()`, which constructs `new Item(...)` and adds an unbound intrusive holder to the registry's internal tracking list.
2. Passes that instance into `RegisterEvent#register(...)` to bind it to a registry key.

If you only cancel step 2 inside `RegisterEvent#register`, step 1 has already happened. The `Item` exists in memory with an unbound intrusive holder. As soon as NeoForge finishes registration and calls `MappedRegistry#freeze()`, the game crashes on startup:

```text
java.lang.IllegalStateException: Some intrusive holders were not registered: [Reference{ResourceKey[minecraft:item / tfmg:lead_ingot]=...}]
```

## How `RegistrateRegistrationMixin` avoids the crash

RingoLib intercepts Registrate one frame earlier, right at the top of `AbstractRegistrate$Registration#register`:

```java
@Pseudo
@Mixin(targets = "com.tterrag.registrate.AbstractRegistrate$Registration", remap = false)
public abstract class RegistrateRegistrationMixin {
    @Shadow @Final private ResourceLocation name;
    @Shadow @Final private ResourceKey<? extends Registry<?>> type;

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private void ringolib$cancelRegistrateRegistration(RegisterEvent event, CallbackInfo ci) {
        if (RegistryRedirectManager.shouldCancel(this.type, this.name)) {
            ci.cancel();
        }
    }
}
```

Because `ci.cancel()` runs before `creator.get()`:
1. `new Item(...)` / `new Block(...)` never executes for the redirected entry.
2. No intrusive holder is ever allocated.
3. `MappedRegistry#freeze()` completes without error.
4. Because `RegistryRedirectManager.applyAliases` already ran at `EventPriority.HIGHEST`, Registrate's `RegistryEntry` and `ItemEntry` lookups for `tfmg:lead_ingot` resolve through the NeoForge alias to the target item (such as `oreganized:lead_ingot`).
5. `@Pseudo` tells SpongePowered Mixin to ignore this class when no mod in the pack bundles Registrate, so RingoLib has no hard runtime dependency on Create or Registrate.
