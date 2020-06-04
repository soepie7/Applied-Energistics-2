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

package appeng.items.misc;


import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.Api;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.helpers.InvalidPatternHelper;
import appeng.helpers.PatternHelper;
import appeng.items.AEBaseItem;
import appeng.util.Platform;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;


public class ItemEncodedPattern extends AEBaseItem implements ICraftingPatternItem
{
	// rather simple client side caching.
	private static final Map<ItemStack, ItemStack> SIMPLE_CACHE = new WeakHashMap<>();

	public ItemEncodedPattern()
	{
		super(new Item.Properties().maxStackSize(1));
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick( final World w, final PlayerEntity player, final Hand hand )
	{
		this.clearPattern( player.getHeldItem( hand ), player );

		return new ActionResult<>( ActionResultType.SUCCESS, player.getHeldItem( hand ) );
	}

	@Override
	public ActionResultType onItemUseFirst( ItemStack stack, ItemUseContext context )
	{
		return this.clearPattern( stack, context.getPlayer() ) ? ActionResultType.SUCCESS : ActionResultType.PASS;
	}

	private boolean clearPattern( final ItemStack stack, final PlayerEntity player )
	{
		if( player.isCrouching() )
		{
			if( Platform.isClient() )
			{
				return false;
			}

			final PlayerInventory inv = player.inventory;

			ItemStack is = Api.INSTANCE.definitions().materials().blankPattern().maybeStack( stack.getCount() ).orElse( ItemStack.EMPTY );
			if( !is.isEmpty() )
			{
				for( int s = 0; s < player.inventory.getSizeInventory(); s++ )
				{
					if( inv.getStackInSlot( s ) == stack )
					{
						inv.setInventorySlotContents( s, is );
						return true;
					}
				}
			}
		}

		return false;
	}

	@Override
	@OnlyIn( Dist.CLIENT )
	public void addInformation( final ItemStack stack, final World world, final List<ITextComponent> lines, final ITooltipFlag advancedTooltips )
	{
		final ICraftingPatternDetails details = this.getPatternForItem( stack, world );

		if( details == null )
		{
			if( !stack.hasTag() )
			{
				return;
			}

			stack.setDisplayName(new TranslationTextComponent(GuiText.InvalidPattern.getTranslationKey())
				.applyTextStyle(TextFormatting.RED));

			InvalidPatternHelper invalid = new InvalidPatternHelper( stack );

			final ITextComponent label = new TranslationTextComponent( invalid.isCraftable() ? GuiText.Crafts.getTranslationKey() : GuiText.Creates.getTranslationKey() ).appendText(": ");
			final ITextComponent and = new StringTextComponent(" ").appendSibling(new TranslationTextComponent(GuiText.And.getTranslationKey())).appendText(" ");
			final ITextComponent with = new TranslationTextComponent(GuiText.With.getTranslationKey()).appendText(": ");

			boolean first = true;
			for( final InvalidPatternHelper.PatternIngredient output : invalid.getOutputs() )
			{
				lines.add( ( first ? label : and ).shallowCopy().appendSibling(output.getFormattedToolTip()) );
				first = false;
			}

			first = true;
			for( final InvalidPatternHelper.PatternIngredient input : invalid.getInputs() )
			{
				lines.add( ( first ? with : and ).shallowCopy().appendSibling(input.getFormattedToolTip()) );
				first = false;
			}

			if( invalid.isCraftable() )
			{
				final ITextComponent substitutionLabel = new TranslationTextComponent(GuiText.Substitute.getTranslationKey()).appendText(" ");
				final ITextComponent canSubstitute = new TranslationTextComponent(invalid.canSubstitute() ? GuiText.Yes.getTranslationKey() : GuiText.No.getTranslationKey());

				lines.add( substitutionLabel.appendSibling(canSubstitute) );
			}

			return;
		}

		if( stack.hasDisplayName() )
		{
			stack.removeChildTag( "display" );
		}

		final boolean isCrafting = details.isCraftable();
		final boolean substitute = details.canSubstitute();

		final IAEItemStack[] in = details.getCondensedInputs();
		final IAEItemStack[] out = details.getCondensedOutputs();

		final ITextComponent label = ( isCrafting ? GuiText.Crafts.textComponent() : GuiText.Creates.textComponent() ).appendText(": ");
		final ITextComponent and = new StringTextComponent(" ").appendSibling(GuiText.And.textComponent()).appendText(" ");
		final ITextComponent with = GuiText.With.textComponent().appendText(": ");

		boolean first = true;
		for( final IAEItemStack anOut : out )
		{
			if( anOut == null )
			{
				continue;
			}

			lines.add( ( first ? label : and ).shallowCopy().appendText(anOut.getStackSize() + " ").appendSibling(Platform.getItemDisplayName( anOut )));
			first = false;
		}

		first = true;
		for( final IAEItemStack anIn : in )
		{
			if( anIn == null )
			{
				continue;
			}

			lines.add((first ? with : and).shallowCopy().appendText(anIn.getStackSize() + " ").appendSibling(Platform.getItemDisplayName(anIn)));
			first = false;
		}

		if( isCrafting )
		{
			final ITextComponent substitutionLabel = GuiText.Substitute.textComponent().appendText(" ");
			final ITextComponent canSubstitute = substitute ? GuiText.Yes.textComponent() : GuiText.No.textComponent();

			lines.add( substitutionLabel.appendSibling(canSubstitute) );
		}
	}

	@Override
	public ICraftingPatternDetails getPatternForItem( final ItemStack is, final World w )
	{
		try
		{
			return new PatternHelper( is, w );
		}
		catch( final Throwable t )
		{
			return null;
		}
	}

	public ItemStack getOutput( final ItemStack item )
	{
		ItemStack out = SIMPLE_CACHE.get( item );

		if( out != null )
		{
			return out;
		}

		final World w = AppEng.proxy.getWorld();
		if( w == null )
		{
			return ItemStack.EMPTY;
		}

		final ICraftingPatternDetails details = this.getPatternForItem( item, w );

		out = details != null ? details.getOutputs()[0].createItemStack() : ItemStack.EMPTY;

		SIMPLE_CACHE.put( item, out );
		return out;
	}
}
