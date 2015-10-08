/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.parts;


import java.util.LinkedList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.Block.SoundType;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import appeng.api.AEApi;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.definitions.IItems;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartItemStack;
import appeng.api.parts.SelectedPart;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.core.CommonHelper;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketClick;
import appeng.core.sync.packets.PacketPartPlacement;
import appeng.facade.IFacadeItem;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.abstraction.IBuildCraftTransport;
import appeng.integration.abstraction.IFMP;
import appeng.util.LookDirection;
import appeng.util.Platform;

import com.google.common.base.Optional;


public class PartPlacement
{

	private static float eyeHeight = 0.0f;
	private final ThreadLocal<Object> placing = new ThreadLocal<Object>();
	private boolean wasCanceled = false;

	public static boolean place( final ItemStack held, final BlockPos pos, EnumFacing side, final EntityPlayer player, final World world, PlaceType pass, final int depth )
	{
		if( depth > 3 )
		{
			return false;
		}

		if( held != null && Platform.isWrench( player, held, pos ) && player.isSneaking() )
		{
			if( !Platform.hasPermissions( new DimensionalCoord( world, pos ), player ) )
			{
				return false;
			}

			final Block block = world.getBlockState( pos ).getBlock();
			final TileEntity tile = world.getTileEntity( pos );
			IPartHost host = null;

			if( tile instanceof IPartHost )
			{
				host = (IPartHost) tile;
			}

			if( host != null )
			{
				if( !world.isRemote )
				{
					final LookDirection dir = Platform.getPlayerRay( player, getEyeOffset( player ) );
					final MovingObjectPosition mop = block.collisionRayTrace( world, pos, dir.getA(), dir.getB() );

					if( mop != null )
					{
						final List<ItemStack> is = new LinkedList<ItemStack>();
						final SelectedPart sp = selectPart( player, host, mop.hitVec.addVector( -mop.getBlockPos().getX(), -mop.getBlockPos().getY(), -mop.getBlockPos().getZ() ) );

						if( sp.part != null )
						{
							is.add( sp.part.getItemStack( PartItemStack.Wrench ) );
							sp.part.getDrops( is, true );
							host.removePart( sp.side, false );
						}

						if( sp.facade != null )
						{
							is.add( sp.facade.getItemStack() );
							host.getFacadeContainer().removeFacade( host, sp.side );
							Platform.notifyBlocksOfNeighbors( world, pos );
						}

						if( host.isEmpty() )
						{
							host.cleanup();
						}

						if( !is.isEmpty() )
						{
							Platform.spawnDrops( world, pos, is );
						}
					}
				}
				else
				{
					player.swingItem();
					NetworkHandler.instance.sendToServer( new PacketPartPlacement( pos, side, getEyeOffset( player ) ) );
				}
				return true;
			}

			return false;
		}

		TileEntity tile = world.getTileEntity( pos );
		IPartHost host = null;

		if( tile instanceof IPartHost )
		{
			host = (IPartHost) tile;
		}

		if( held != null )
		{
			final IFacadePart fp = isFacade( held, AEPartLocation.fromFacing( side ) );
			if( fp != null )
			{
				if( host != null )
				{
					if( !world.isRemote )
					{
						if( host.getPart( AEPartLocation.INTERNAL ) == null )
						{
							return false;
						}

						if( host.canAddPart( held, AEPartLocation.fromFacing( side ) ) )
						{
							if( host.getFacadeContainer().addFacade( fp ) )
							{
								host.markForUpdate();
								if( !player.capabilities.isCreativeMode )
								{
									held.stackSize--;
									if( held.stackSize == 0 )
									{
										player.inventory.mainInventory[player.inventory.currentItem] = null;
										MinecraftForge.EVENT_BUS.post( new PlayerDestroyItemEvent( player, held ) );
									}
								}
								return true;
							}
						}
					}
					else
					{
						player.swingItem();
						NetworkHandler.instance.sendToServer( new PacketPartPlacement( pos, side, getEyeOffset( player ) ) );
						return true;
					}
				}
				return false;
			}
		}

		// TODO: IFMP INTEGRATION
		// TODO IIMMIBISMICROBLOCKS INTEGRATION

		/*
		 * if( host == null && tile != null && IntegrationRegistry.INSTANCE.isEnabled( IntegrationType.FMP ) )
		 * {
		 * host = ( (IFMP) IntegrationRegistry.INSTANCE.getInstance( IntegrationType.FMP ) ).getOrCreateHost( tile );
		 * }
		 * if( host == null && tile != null && IntegrationRegistry.INSTANCE.isEnabled(
		 * IntegrationType.ImmibisMicroblocks ) )
		 * {
		 * host = ( (IImmibisMicroblocks) IntegrationRegistry.INSTANCE.getInstance( IntegrationType.ImmibisMicroblocks )
		 * ).getOrCreateHost( player, face, tile );
		 * }
		 */

		// if ( held == null )
		{
			final Block block = world.getBlockState( pos ).getBlock();
			if( host != null && player.isSneaking() && block != null )
			{
				final LookDirection dir = Platform.getPlayerRay( player, getEyeOffset( player ) );
				final MovingObjectPosition mop = block.collisionRayTrace( world, pos, dir.getA(), dir.getB() );

				if( mop != null )
				{
					mop.hitVec = mop.hitVec.addVector( -mop.getBlockPos().getX(), -mop.getBlockPos().getY(), -mop.getBlockPos().getZ() );
					final SelectedPart sPart = selectPart( player, host, mop.hitVec );
					if( sPart != null && sPart.part != null )
					{
						if( sPart.part.onShiftActivate( player, mop.hitVec ) )
						{
							if( world.isRemote )
							{
								NetworkHandler.instance.sendToServer( new PacketPartPlacement( pos, side, getEyeOffset( player ) ) );
							}
							return true;
						}
					}
				}
			}
		}

		if( held == null || !( held.getItem() instanceof IPartItem ) )
		{
			return false;
		}

		BlockPos te_pos = pos;

		final IBlockDefinition multiPart = AEApi.instance().definitions().blocks().multiPart();
		if( host == null && pass == PlaceType.PLACE_ITEM )
		{
			EnumFacing offset = null;

			final Block blkID = world.getBlockState( pos ).getBlock();
			if( blkID != null && !blkID.isReplaceable( world, pos ) )
			{
				offset = side;
				if( Platform.isServer() )
				{
					side = side.getOpposite();
				}
			}

			te_pos = offset == null ? pos : pos.offset( offset );

			tile = world.getTileEntity( te_pos );
			if( tile instanceof IPartHost )
			{
				host = (IPartHost) tile;
			}

			// TODO: IFMP INTEGRATION
			// TODO IIMMIBISMICROBLOCKS INTEGRATION

			/*
			 * if( host == null && tile != null && IntegrationRegistry.INSTANCE.isEnabled( IntegrationType.FMP ) )
			 * {
			 * host = ( (IFMP) IntegrationRegistry.INSTANCE.getInstance( IntegrationType.FMP ) ).getOrCreateHost( tile
			 * );
			 * }
			 * if( host == null && tile != null && IntegrationRegistry.INSTANCE.isEnabled(
			 * IntegrationType.ImmibisMicroblocks ) )
			 * {
			 * host = ( (IImmibisMicroblocks) IntegrationRegistry.INSTANCE.getInstance(
			 * IntegrationType.ImmibisMicroblocks ) ).getOrCreateHost( player, side, tile );
			 * }
			 */

			final Optional<ItemStack> maybeMultiPartStack = multiPart.maybeStack( 1 );
			final Optional<Block> maybeMultiPartBlock = multiPart.maybeBlock();
			final Optional<ItemBlock> maybeMultiPartItemBlock = multiPart.maybeItemBlock();

			final boolean hostIsNotPresent = host == null;
			final boolean multiPartPresent = maybeMultiPartBlock.isPresent() && maybeMultiPartStack.isPresent() && maybeMultiPartItemBlock.isPresent();
			final boolean canMultiPartBePlaced = maybeMultiPartBlock.get().canPlaceBlockAt( world, te_pos );

			if( hostIsNotPresent && multiPartPresent && canMultiPartBePlaced && maybeMultiPartItemBlock.get().placeBlockAt( maybeMultiPartStack.get(), player, world, te_pos, side, 0.5f, 0.5f, 0.5f, maybeMultiPartBlock.get().getDefaultState() ) )
			{
				if( !world.isRemote )
				{
					tile = world.getTileEntity( te_pos );

					if( tile instanceof IPartHost )
					{
						host = (IPartHost) tile;
					}

					pass = PlaceType.INTERACT_SECOND_PASS;
				}
				else
				{
					player.swingItem();
					NetworkHandler.instance.sendToServer( new PacketPartPlacement( pos, side, getEyeOffset( player ) ) );
					return true;
				}
			}
			else if( host != null && !host.canAddPart( held, AEPartLocation.fromFacing( side ) ) )
			{
				return false;
			}
		}

		if( host == null )
		{
			return false;
		}

		if( !host.canAddPart( held, AEPartLocation.fromFacing( side ) ) )
		{
			if( pass == PlaceType.INTERACT_FIRST_PASS || pass == PlaceType.PLACE_ITEM )
			{
				te_pos = pos.offset( side );

				final Block blkID = world.getBlockState( te_pos ).getBlock();
				tile = world.getTileEntity( te_pos );

				if( tile != null && IntegrationRegistry.INSTANCE.isEnabled( IntegrationType.FMP ) )
				{
					host = ( (IFMP) IntegrationRegistry.INSTANCE.getInstance( IntegrationType.FMP ) ).getOrCreateHost( tile );
				}

				if( ( blkID == null || blkID.isReplaceable( world, te_pos ) || host != null ) ) // /&& side !=
																								// AEPartLocation.INTERNAL
																								// )
				{
					return place( held, te_pos, side.getOpposite(), player, world, pass == PlaceType.INTERACT_FIRST_PASS ? PlaceType.INTERACT_SECOND_PASS : PlaceType.PLACE_ITEM, depth + 1 );
				}
			}
			return false;
		}

		if( !world.isRemote )
		{
			final Block block = world.getBlockState( pos ).getBlock();
			final LookDirection dir = Platform.getPlayerRay( player, getEyeOffset( player ) );
			final MovingObjectPosition mop = block.collisionRayTrace( world, pos, dir.getA(), dir.getB() );

			if( mop != null )
			{
				final SelectedPart sp = selectPart( player, host, mop.hitVec.addVector( -mop.getBlockPos().getX(), -mop.getBlockPos().getY(), -mop.getBlockPos().getZ() ) );

				if( sp.part != null )
				{
					if( !player.isSneaking() && sp.part.onActivate( player, mop.hitVec ) )
					{
						return false;
					}
				}
			}

			final DimensionalCoord dc = host.getLocation();
			if( !Platform.hasPermissions( dc, player ) )
			{
				return false;
			}

			final AEPartLocation mySide = host.addPart( held, AEPartLocation.fromFacing( side ), player );
			if( mySide != null )
			{
				for( final Block multiPartBlock : multiPart.maybeBlock().asSet() )
				{
					final SoundType ss = multiPartBlock.stepSound;

					world.playSoundEffect( 0.5 + pos.getX(), 0.5 + pos.getY(), 0.5 + pos.getZ(), ss.getPlaceSound(), ( ss.getVolume() + 1.0F ) / 2.0F, ss.getFrequency() * 0.8F );
				}

				if( !player.capabilities.isCreativeMode )
				{
					held.stackSize--;
					if( held.stackSize == 0 )
					{
						player.inventory.mainInventory[player.inventory.currentItem] = null;
						MinecraftForge.EVENT_BUS.post( new PlayerDestroyItemEvent( player, held ) );
					}
				}
			}
		}
		else
		{
			player.swingItem();
			NetworkHandler.instance.sendToServer( new PacketPartPlacement( pos, side, getEyeOffset( player ) ) );
		}
		return true;
	}

	private static float getEyeOffset( final EntityPlayer p )
	{
		if( p.worldObj.isRemote )
		{
			return Platform.getEyeOffset( p );
		}

		return getEyeHeight();
	}

	private static SelectedPart selectPart( final EntityPlayer player, final IPartHost host, final Vec3 pos )
	{
		CommonHelper.proxy.updateRenderMode( player );
		final SelectedPart sp = host.selectPart( pos );
		CommonHelper.proxy.updateRenderMode( null );

		return sp;
	}

	public static IFacadePart isFacade( final ItemStack held, final AEPartLocation side )
	{
		if( held.getItem() instanceof IFacadeItem )
		{
			return ( (IFacadeItem) held.getItem() ).createPartFromItemStack( held, side );
		}

		if( IntegrationRegistry.INSTANCE.isEnabled( IntegrationType.BuildCraftTransport ) )
		{
			final IBuildCraftTransport bc = (IBuildCraftTransport) IntegrationRegistry.INSTANCE.getInstance( IntegrationType.BuildCraftTransport );
			if( bc.isFacade( held ) )
			{
				return bc.createFacadePart( held, side );
			}
		}

		return null;
	}

	@SubscribeEvent
	public void playerInteract( final TickEvent.ClientTickEvent event )
	{
		this.wasCanceled = false;
	}

	@SubscribeEvent
	public void playerInteract( final PlayerInteractEvent event )
	{
		if( event.action == Action.RIGHT_CLICK_AIR && event.entityPlayer.worldObj.isRemote )
		{
			// re-check to see if this event was already channeled, cause these two events are really stupid...
			final MovingObjectPosition mop = Platform.rayTrace( event.entityPlayer, true, false );
			final Minecraft mc = Minecraft.getMinecraft();

			final float f = 1.0F;
			final double d0 = mc.playerController.getBlockReachDistance();
			final Vec3 vec3 = mc.getRenderViewEntity().getPositionEyes( f );

			if( mop != null && mop.hitVec.distanceTo( vec3 ) < d0 )
			{
				final World w = event.entity.worldObj;
				final TileEntity te = w.getTileEntity( mop.getBlockPos() );
				if( te instanceof IPartHost && this.wasCanceled )
				{
					event.setCanceled( true );
				}
			}
			else
			{
				final ItemStack held = event.entityPlayer.getHeldItem();
				final IItems items = AEApi.instance().definitions().items();

				boolean supportedItem = items.memoryCard().isSameAs( held );
				supportedItem |= items.colorApplicator().isSameAs( held );

				if( event.entityPlayer.isSneaking() && held != null && supportedItem )
				{
					NetworkHandler.instance.sendToServer( new PacketClick( event.pos, event.face, 0, 0, 0 ) );
				}
			}
		}
		else if( event.action == Action.RIGHT_CLICK_BLOCK && event.entityPlayer.worldObj.isRemote )
		{
			if( this.placing.get() != null )
			{
				return;
			}

			this.placing.set( event );

			final ItemStack held = event.entityPlayer.getHeldItem();
			if( place( held, event.pos, event.face, event.entityPlayer, event.entityPlayer.worldObj, PlaceType.INTERACT_FIRST_PASS, 0 ) )
			{
				event.setCanceled( true );
				this.wasCanceled = true;
			}

			this.placing.set( null );
		}
	}

	private static float getEyeHeight()
	{
		return eyeHeight;
	}

	public static void setEyeHeight( final float eyeHeight )
	{
		PartPlacement.eyeHeight = eyeHeight;
	}

	public enum PlaceType
	{
		PLACE_ITEM, INTERACT_FIRST_PASS, INTERACT_SECOND_PASS
	}
}