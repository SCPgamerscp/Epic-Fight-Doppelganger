package scpgamerscp.efdoppelganger.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WeaponMemory implements INBTSerializable<CompoundTag> {
    private final Map<ResourceLocation, ItemStack> weapons = new LinkedHashMap<>();
    private final Map<ResourceLocation, ItemStack> heals = new LinkedHashMap<>();
    private final List<String> skills = new ArrayList<>();

    public void rememberWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        weapons.put(id, stack.copy());
    }

    public void rememberHeal(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        heals.put(id, stack.copy());
    }

    public void rememberSkill(String skillId) {
        if (skillId == null || skillId.isBlank() || skills.contains(skillId)) {
            return;
        }
        skills.add(skillId);
    }

    public List<ItemStack> weapons() {
        return new ArrayList<>(weapons.values());
    }

    public List<ItemStack> heals() {
        return new ArrayList<>(heals.values());
    }

    public List<String> skills() {
        return new ArrayList<>(skills);
    }

    public boolean knows(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && weapons.containsKey(id);
    }

    public void copyFrom(WeaponMemory other) {
        weapons.clear();
        heals.clear();
        skills.clear();
        other.weapons.forEach((k, v) -> weapons.put(k, v.copy()));
        other.heals.forEach((k, v) -> heals.put(k, v.copy()));
        skills.addAll(other.skills);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Weapons", writeStacks(weapons));
        tag.put("Heals", writeStacks(heals));
        ListTag skillList = new ListTag();
        for (String s : skills) {
            skillList.add(StringTag.valueOf(s));
        }
        tag.put("Skills", skillList);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        weapons.clear();
        heals.clear();
        skills.clear();
        readStacks(nbt.getList("Weapons", Tag.TAG_COMPOUND), weapons);
        readStacks(nbt.getList("Heals", Tag.TAG_COMPOUND), heals);
        ListTag skillList = nbt.getList("Skills", Tag.TAG_STRING);
        for (int i = 0; i < skillList.size(); i++) {
            skills.add(skillList.getString(i));
        }
    }

    private static ListTag writeStacks(Map<ResourceLocation, ItemStack> map) {
        ListTag list = new ListTag();
        map.forEach((id, stack) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id.toString());
            entry.put("Stack", stack.save(new CompoundTag()));
            list.add(entry);
        });
        return list;
    }

    private static void readStacks(ListTag list, Map<ResourceLocation, ItemStack> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("Id"));
            ItemStack stack = ItemStack.of(entry.getCompound("Stack"));
            if (id != null && !stack.isEmpty()) {
                into.put(id, stack);
            }
        }
    }
}
