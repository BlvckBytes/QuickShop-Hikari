package com.ghostchu.quickshop.menu;
/*
 * QuickShop-Hikari
 * Copyright (C) 2024 Daniel "creatorfromhell" Vidmar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.menu.history.MainPage;
import net.tnemc.menu.core.Menu;
import net.tnemc.menu.core.Page;

/**
 * ShopHistoryMenu
 *
 * @author creatorfromhell
 * @since 6.2.0.8
 */
public class ShopHistoryMenu extends Menu {

  public static final String SHOPS_DATA = "SHOPS_LIST";
  public static final String HISTORY_RECORDS = "HISTORY_RECORDS";
  public static final String HISTORY_SUMMARY = "HISTORY_SUMMARY";
  public static final String SHOPS_PAGE = "SHOPS_PAGE_ID";

  public ShopHistoryMenu() {

    this.rows = 6;
    this.name = "qs:history";

    setOpen((open)->open.getMenu().setTitle(QuickShop.getInstance().text().of(open.getPlayer().identifier(), "history.shop.gui-title").legacy()));

    final Page main = new Page(1);
    final MainPage mainPageOpen = new MainPage(this.name, this.name, 1, 1, SHOPS_PAGE, this.rows, "history.shop.log-icon-description-with-store-name");
    main.setOpen(mainPageOpen::handle);
    addPage(main);
  }
}