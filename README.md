# Fabric Kotlin Boilerplate

Simple Minecraft Fabric mod boilerplate written in Kotlin.

Before you try anything, I recommend you read the sections you're interested in
on the [Fabric wiki](https://fabricmc.net/wiki/doku.php#developing_with_fabric)
to get acquainted with some of the concepts you'll need to work with. 

## How to use

This repository is marked as template, so you can just click on
`Use this template` to create a repository based on this one. Rename and edit
files to fit your mod.

### Add a block

To add a simple block, you only need to add an entry in the block registry helper:

```kotlin
object Blocks {
    val YOURBLOCK = register("your_block_id", Block(FabricBlockSettings.of(Material.LEAVES).hardness(1f)))
    // ...
}
```

Then add or edit the corresponding resources, changing the values as you need:

- `src/resources/assets/your_namespace`
    - `blockstates/your_block_id.json`
      ```json
      {
      }
      ```
    - `lang/*.json`
      ```json
      {
        "block.your_namespace.your_block_id": "Name to be displayed in language"
      }
      ```
    - `models/your_model_id.json`
      ```json
      {
        "parent": "block/cube_all",
        "textures": {
          "all": "your_namespace:block/your_block_id"
        }
      }
      ```
    - `textures/your_texture.png`

#### Add a block item

#### Add complex behaviour

To add complex behaviour to your block, you'll need to define a class in which
to define the different components you need.  Depending on the kind of
behaviour you want to add to the block, you'll need to add different
components. To know what to add, I recommend you read the
[Fabric wiki](https://fabricmc.net/wiki/doku.php#developing_with_fabric).

As an example, here's a test block using
[different states](https://fabricmc.net/wiki/tutorial:blockstate) as well as
[a block entity](https://fabricmc.net/wiki/tutorial:blockentity) to implement
the following behaviour:

> - Define (`BlockTest`):
>     - State `COLOUR` as an integer property, bound to `[0;4]`.
>     - State `COLOURFIXED` as a boolean property.
> - When placed (`::Blocktest`):
>     - Set state `COLOUR` to 0.
>     - Set state `COLOURFIXED` to `false`.
> - On tick (`BlockTestEntity::tick`):
>     - If state `COLOURFIXED` is `false`:
>         - Set `COLOUR` to a random integer value in `[0;4]`.
>     - Else:
>         - Do nothing.
> - On use (`BlockTest::onUse`):
>     - If the item the player holds is `FLINT_AND_STEEL`:
>         - Toggle state `COLOURFIXED`.
>     - Else:
>         - Do nothing.

<details>
<summary>Code</summary>

```kotlin
class BlockTest(settings: Settings?) : Block(settings), BlockEntityProvider {
    companion object {
        val COLOURFIXED: BooleanProperty = BooleanProperty.of("colour_fixed")
        val COLOUR: IntProperty = IntProperty.of("colour", 0, 4)
    }

    init {
        defaultState = stateManager.defaultState.with(COLOURFIXED, false).with(COLOUR, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>?) {
        builder?.add(COLOURFIXED)
        builder?.add(COLOUR)
    }

    override fun createBlockEntity(world: BlockView?): BlockEntity = BlockTestEntity()

    override fun onUse(
        state: BlockState?,
        world: World?,
        pos: BlockPos?,
        player: PlayerEntity?,
        hand: Hand?,
        hit: BlockHitResult?
    ): ActionResult {
        if (!world?.isClient!!) {
            if (player?.getStackInHand(hand)?.item?.name?.string.equals(Items.FLINT_AND_STEEL.name.string)) {
                world.setBlockState(pos, state?.with(COLOURFIXED, !state.get(COLOURFIXED)))
                return ActionResult.SUCCESS
            }
        }
        return ActionResult.PASS
    }
}

class BlockTestEntity : BlockEntity(Blocks.Entities.TEST), Tickable {
    private val rng = Random()
    override fun tick() {
        if (!cachedState.get(BlockTest.COLOURFIXED)) Objects.requireNonNull(getWorld())
            ?.setBlockState(pos, cachedState.with(BlockTest.COLOUR, rng.nextInt(5)))
    }
}
```

</details>

### Add an item