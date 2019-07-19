/*
 * Copyright (c) 2018 kulers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.inventorytags;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetMenuOptionClicked;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Inventory Tags",
	description = "Add the ability to tag items in your inventory",
	tags = {"highlight", "items", "overlay", "tagging"},
	enabledByDefault = false
)
@Singleton
public class InventoryTagsPlugin extends Plugin
{
	private static final String ITEM_KEY_PREFIX = "item_";

	private static final String SETNAME_GROUP_1 = "Group 1";
	private static final String SETNAME_GROUP_2 = "Group 2";
	private static final String SETNAME_GROUP_3 = "Group 3";
	private static final String SETNAME_GROUP_4 = "Group 4";

	private static final String CONFIGURE = "Configure";
	private static final String SAVE = "Save";
	private static final String MENU_TARGET = "Inventory Tags";
	private static final String MENU_SET = "Mark";
	private static final String MENU_REMOVE = "Remove";

	private static final WidgetMenuOption FIXED_INVENTORY_TAB_CONFIGURE = new WidgetMenuOption(CONFIGURE,
		MENU_TARGET, WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB);
	private static final WidgetMenuOption FIXED_INVENTORY_TAB_SAVE = new WidgetMenuOption(SAVE,
		MENU_TARGET, WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB);
	private static final WidgetMenuOption RESIZABLE_INVENTORY_TAB_CONFIGURE = new WidgetMenuOption(CONFIGURE,
		MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_TAB);
	private static final WidgetMenuOption RESIZABLE_INVENTORY_TAB_SAVE = new WidgetMenuOption(SAVE,
		MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_TAB);
	private static final WidgetMenuOption RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_CONFIGURE = new WidgetMenuOption(CONFIGURE,
		MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB);
	private static final WidgetMenuOption RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_SAVE = new WidgetMenuOption(SAVE,
		MENU_TARGET, WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB);

	private static final List<String> GROUPS = ImmutableList.of(SETNAME_GROUP_4, SETNAME_GROUP_3, SETNAME_GROUP_2, SETNAME_GROUP_1);

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private InventoryTagsConfig config;

	@Inject
	private MenuManager menuManager;

	@Inject
	private InventoryTagsOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	private boolean editorMode;

	private Color group1Color;
	private Color group2Color;
	private Color group3Color;
	private Color group4Color;

	@Provides
	InventoryTagsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InventoryTagsConfig.class);
	}

	String getTag(int itemId)
	{
		String tag = configManager.getConfiguration(InventoryTagsConfig.GROUP, ITEM_KEY_PREFIX + itemId);
		if (tag == null || tag.isEmpty())
		{
			return null;
		}

		return tag;
	}

	private void setTag(int itemId, String tag)
	{
		configManager.setConfiguration(InventoryTagsConfig.GROUP, ITEM_KEY_PREFIX + itemId, tag);
	}

	private void unsetTag(int itemId)
	{
		configManager.unsetConfiguration(InventoryTagsConfig.GROUP, ITEM_KEY_PREFIX + itemId);
	}

	@Override
	protected void startUp() throws Exception
	{
		updateConfig();
		addSubscriptions();

		refreshInventoryMenuOptions();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(this);

		removeInventoryMenuOptions();
		overlayManager.remove(overlay);
		editorMode = false;
	}

	private void addSubscriptions()
	{
		eventBus.subscribe(ConfigChanged.class, this, this::onConfigChanged);
		eventBus.subscribe(WidgetMenuOptionClicked.class, this, this::onWidgetMenuOptionClicked);
		eventBus.subscribe(MenuOptionClicked.class, this, this::onMenuOptionClicked);
		eventBus.subscribe(MenuOpened.class, this, this::onMenuOpened);
	}

	private void onWidgetMenuOptionClicked(final WidgetMenuOptionClicked event)
	{
		if (event.getWidget() == WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB
			|| event.getWidget() == WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_TAB
			|| event.getWidget() == WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB)
		{
			editorMode = event.getMenuOption().equals(CONFIGURE) && Text.removeTags(event.getMenuTarget()).equals(MENU_TARGET);
			refreshInventoryMenuOptions();
		}
	}

	private void onMenuOptionClicked(final MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.RUNELITE)
		{
			return;
		}

		final String selectedMenu = Text.removeTags(event.getTarget());

		if (event.getOption().equals(MENU_SET))
		{
			setTag(event.getIdentifier(), selectedMenu);
		}
		else if (event.getOption().equals(MENU_REMOVE))
		{
			unsetTag(event.getIdentifier());
		}
	}

	private void onMenuOpened(final MenuOpened event)
	{
		final MenuEntry firstEntry = event.getFirstEntry();

		if (firstEntry == null)
		{
			return;
		}

		final int widgetId = firstEntry.getParam1();

		// Inventory item menu
		if (widgetId == WidgetInfo.INVENTORY.getId() && editorMode)
		{
			int itemId = firstEntry.getIdentifier();

			if (itemId == -1)
			{
				return;
			}

			MenuEntry[] menuList = new MenuEntry[GROUPS.size() + 1];
			int num = 0;

			// preserve the 'Cancel' option as the client will reuse the first entry for Cancel and only resets option/action
			menuList[num++] = event.getMenuEntries()[0];

			for (final String groupName : GROUPS)
			{
				final String group = getTag(itemId);
				final MenuEntry newMenu = new MenuEntry();
				final Color color = getGroupNameColor(groupName);
				newMenu.setOption(groupName.equals(group) ? MENU_REMOVE : MENU_SET);
				newMenu.setTarget(ColorUtil.prependColorTag(groupName, MoreObjects.firstNonNull(color, Color.WHITE)));
				newMenu.setIdentifier(itemId);
				newMenu.setParam1(widgetId);
				newMenu.setType(MenuAction.RUNELITE.getId());
				menuList[num++] = newMenu;
			}

			// Need to set the event entries to prevent conflicts
			event.setMenuEntries(menuList);
			client.setMenuEntries(menuList);
		}
	}

	Color getGroupNameColor(final String name)
	{
		switch (name)
		{
			case SETNAME_GROUP_1:
				return this.group1Color;
			case SETNAME_GROUP_2:
				return this.group2Color;
			case SETNAME_GROUP_3:
				return this.group3Color;
			case SETNAME_GROUP_4:
				return this.group4Color;
		}

		return null;
	}

	private void removeInventoryMenuOptions()
	{
		menuManager.removeManagedCustomMenu(FIXED_INVENTORY_TAB_CONFIGURE);
		menuManager.removeManagedCustomMenu(FIXED_INVENTORY_TAB_SAVE);
		menuManager.removeManagedCustomMenu(RESIZABLE_INVENTORY_TAB_CONFIGURE);
		menuManager.removeManagedCustomMenu(RESIZABLE_INVENTORY_TAB_SAVE);
		menuManager.removeManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_CONFIGURE);
		menuManager.removeManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_SAVE);
	}

	private void refreshInventoryMenuOptions()
	{
		removeInventoryMenuOptions();
		if (editorMode)
		{
			menuManager.addManagedCustomMenu(FIXED_INVENTORY_TAB_SAVE);
			menuManager.addManagedCustomMenu(RESIZABLE_INVENTORY_TAB_SAVE);
			menuManager.addManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_SAVE);
		}
		else
		{
			menuManager.addManagedCustomMenu(FIXED_INVENTORY_TAB_CONFIGURE);
			menuManager.addManagedCustomMenu(RESIZABLE_INVENTORY_TAB_CONFIGURE);
			menuManager.addManagedCustomMenu(RESIZABLE_BOTTOM_LINE_INVENTORY_TAB_CONFIGURE);
		}
	}

	private void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("inventorytags"))
		{
			updateConfig();
		}
	}

	private void updateConfig()
	{
		this.group1Color = config.getGroup1Color();
		this.group2Color = config.getGroup2Color();
		this.group3Color = config.getGroup3Color();
		this.group4Color = config.getGroup4Color();
	}
}
