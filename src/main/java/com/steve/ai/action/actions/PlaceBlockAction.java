package com.steve.ai.action.actions;

import com.steve.ai.action.ActionResult;
import com.steve.ai.action.Task;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class PlaceBlockAction extends BaseAction {
    private Block blockToPlace;
    private BlockPos targetPos;
    private int ticksRunning;
    private static final int MAX_TICKS = 200;

    public PlaceBlockAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        String blockName = task.getStringParameter("block");
        int x = task.getIntParameter("x", 0);
        int y = task.getIntParameter("y", 0);
        int z = task.getIntParameter("z", 0);
        
        targetPos = new BlockPos(x, y, z);
        ticksRunning = 0;
        
        blockToPlace = parseBlock(blockName);
        
        if (blockToPlace == null || blockToPlace == Blocks.AIR) {
            result = ActionResult.failure("Invalid block type: " + blockName);
            return;
        }
        
    }

    @Override
    protected void onTick() {
        ticksRunning++;

        if (ticksRunning > MAX_TICKS) {
            result = ActionResult.failure("Place block timeout");
            return;
        }

        if (!steve.blockPosition().closerThan(targetPos, 5.0)) {
            steve.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
            return;
        }

        // Check material (skip in creative mode)
        boolean creative = SteveConfig.CREATIVE_MODE.get();
        if (!creative) {
            if (!steve.hasBlock(blockToPlace, 1)) {
                result = ActionResult.failure("No " + blockToPlace.getName().getString() + " in inventory");
                return;
            }
            // Consume material from inventory
            steve.removeBlockFromInventory(blockToPlace, 1);
        }

        BlockState currentState = steve.level().getBlockState(targetPos);
        if (!currentState.isAir() && !currentState.liquid()) {
            result = ActionResult.failure("Position is not empty");
            return;
        }

        steve.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blockToPlace.asItem()));
        steve.swing(InteractionHand.MAIN_HAND, true);

        steve.level().setBlock(targetPos, blockToPlace.defaultBlockState(), 3);
        result = ActionResult.success("Placed " + blockToPlace.getName().getString());
    }

    @Override
    protected void onCancel() {
        steve.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        steve.getNavigation().stop();
    }

    @Override
    public String getDescription() {
        return "Place " + blockToPlace.getName().getString() + " at " + targetPos;
    }

    private Block parseBlock(String blockName) {
        blockName = blockName.toLowerCase().replace(" ", "_");
        if (!blockName.contains(":")) {
            blockName = "minecraft:" + blockName;
        }
        ResourceLocation resourceLocation = new ResourceLocation(blockName);
        return BuiltInRegistries.BLOCK.get(resourceLocation);
    }
}

