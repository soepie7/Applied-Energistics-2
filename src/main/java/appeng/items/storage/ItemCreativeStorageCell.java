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

package appeng.items.storage;


import java.util.List;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.core.Api;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;


public class ItemCreativeStorageCell extends AEBaseItem implements ICellWorkbenchItem
{

	public ItemCreativeStorageCell(Properties props)
	{
		super(props);
		// FIXME this.setMaxStackSize( 1 );
	}

	@Override
	public boolean isEditable( final ItemStack is )
	{
		return true;
	}

	@Override
	public IItemHandler getUpgradesInventory( final ItemStack is )
	{
		return null;
	}

	@Override
	public IItemHandler getConfigInventory( final ItemStack is )
	{
		return new CellConfig( is );
	}

	@Override
	public FuzzyMode getFuzzyMode( final ItemStack is )
	{
		return FuzzyMode.IGNORE_ALL;
	}

	@Override
	public void setFuzzyMode( final ItemStack is, final FuzzyMode fzMode )
	{

	}

	@OnlyIn( Dist.CLIENT )
	@Override
	public void addInformation(final ItemStack stack, final World world, final List<ITextComponent> lines, final ITooltipFlag advancedTooltips )
	{
		final IMEInventoryHandler<?> inventory = Api.INSTANCE
				.registries()
				.cell()
				.getCellInventory( stack, null,
						Api.INSTANCE.storage().getStorageChannel( IItemStorageChannel.class ) );

		if( inventory instanceof ICellInventoryHandler )
		{
			final CellConfig cc = new CellConfig( stack );

			for( final ItemStack is : cc )
			{
				if( !is.isEmpty() )
				{
					lines.add( is.getDisplayName() );
				}
			}
		}
	}
}
