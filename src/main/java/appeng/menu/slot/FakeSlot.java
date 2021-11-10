/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.menu.slot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;

public class FakeSlot extends AppEngSlot {
    public FakeSlot(InternalInventory inv, final int invSlot) {
        super(inv, invSlot);
    }

    @Override
    public void onTake(final Player player, final ItemStack stack) {
    }

    @Override
    public ItemStack remove(final int par1) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return false;
    }

    @Override
    public void set(ItemStack is) {
        if (!is.isEmpty()) {
            is = is.copy();
        }

        super.set(is);
    }

    @Override
    public boolean mayPickup(final Player player) {
        return false;
    }

    // Used by REI/JEI dragging ghost items to determine if this is a valid destination
    public boolean canSetFilterTo(ItemStack stack) {
        return slot < getInventory().size() && getInventory().isItemValid(slot, stack);
    }

    public void increase(ItemStack is) {
        // Special support for increasing the configured stocking amount by simply clicking
        if (getInventory() instanceof ConfigMenuInventory configInv) {
            var realInv = configInv.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                var newFilter = configInv.convertToSuitableStack(is);
                if (newFilter != null) {
                    realInv.insert(slot, newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }
        }

        set(is);
    }

    public void decrease(ItemStack is) {
        // Special support for modern config mode inventories
        if (getInventory() instanceof ConfigMenuInventory configInv) {
            var realInv = configInv.getDelegate();
            if (realInv.getMode() == ConfigInventory.Mode.CONFIG_STACKS) {
                var newFilter = configInv.convertToSuitableStack(is);

                if (newFilter != null) {
                    realInv.extract(slot, newFilter.what(), newFilter.amount(), Actionable.MODULATE);
                    return;
                }
            }
        }

        is = is.copy();
        if (is.isEmpty()) {
            is.setCount(Math.max(1, is.getCount() - 1));
        } else if (is.sameItem(is)) {
            is.setCount(Math.min(is.getMaxStackSize(), is.getCount() + 1));
        } else {
            is.setCount(1);
        }

        set(is);
    }
}
