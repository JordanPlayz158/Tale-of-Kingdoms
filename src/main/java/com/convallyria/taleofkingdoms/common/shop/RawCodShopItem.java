package com.convallyria.taleofkingdoms.common.shop;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class RawCodShopItem extends ShopItem {

    @Override
    public int getCost() {
        return 18;
    }

    @Override
    public Item getItem() {
        return Items.COD;
    }

    @Override
    public String getName() {
        return "Raw Cod";
    }
}