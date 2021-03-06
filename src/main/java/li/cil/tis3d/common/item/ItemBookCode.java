package li.cil.tis3d.common.item;

import li.cil.tis3d.api.machine.Casing;
import li.cil.tis3d.client.gui.GuiHandlerClient;
import li.cil.tis3d.common.Constants;
import li.cil.tis3d.common.TIS3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The code book, utility book for coding ASM programs for execution modules.
 */
public final class ItemBookCode extends ItemBook {
    public ItemBookCode() {
        setMaxStackSize(1);
    }

    // --------------------------------------------------------------------- //
    // Item

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer playerIn, final List<String> tooltip, final boolean advanced) {
        super.addInformation(stack, playerIn, tooltip, advanced);
        final String info = I18n.format(Constants.TOOLTIP_BOOK_CODE);
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        tooltip.addAll(fontRenderer.listFormattedStringToWidth(info, Constants.MAX_TOOLTIP_WIDTH));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player, final EnumHand hand) {
        if (world.isRemote) {
            player.openGui(TIS3D.instance, GuiHandlerClient.GuiId.BOOK_CODE.ordinal(), world, 0, 0, 0);
        }
        return super.onItemRightClick(world, player, hand);
    }

    @Override
    public boolean doesSneakBypassUse(final ItemStack stack, final IBlockAccess world, final BlockPos pos, final EntityPlayer player) {
        return world.getTileEntity(pos) instanceof Casing;
    }

    // --------------------------------------------------------------------- //
    // ItemBook

    @Override
    public boolean isEnchantable(final ItemStack stack) {
        return false;
    }

    @Override
    public int getItemEnchantability() {
        return 0;
    }

    // --------------------------------------------------------------------- //

    /**
     * Wrapper for list of pages stored in the code book.
     */
    public static class Data {
        private static final String CONTINUATION_MACRO = "#BWTM";
        private static final String TAG_PAGES = "pages";
        private static final String TAG_SELECTED = "selected";

        private final List<List<String>> pages = new ArrayList<>();
        private int selectedPage = 0;

        // --------------------------------------------------------------------- //

        /**
         * Get the page currently selected in the book.
         *
         * @return the index of the selected page.
         */
        public int getSelectedPage() {
            return selectedPage;
        }

        /**
         * Set which page is currently selected.
         *
         * @param index the new selected index.
         */
        public void setSelectedPage(final int index) {
            this.selectedPage = index;
            validateSelectedPage();
        }

        /**
         * Get the number of pages stored in the book.
         *
         * @return the number of pages stored in the book.
         */
        public int getPageCount() {
            return pages.size();
        }

        /**
         * Get the code on the specified page.
         *
         * @param index the index of the page to get the code of.
         * @return the code on the page.
         */
        public List<String> getPage(final int index) {
            return Collections.unmodifiableList(pages.get(index));
        }

        /**
         * Add a new, blank page to the book.
         */
        public void addPage() {
            addOrSelectProgram(Collections.singletonList(""));
        }

        /**
         * Add a new program to the book.
         * <p>
         * Depending on the size of the program, this will generate multiple pages
         * and automatically insert <code>#BWTM</code> preprocessor macros as
         * necessary (when they're not already there).
         * <p>
         * If the provided program is already present in the code book letter by
         * letter, then instead of adding the provided code, the already present
         * program will be selected instead.
         *
         * @param code the code to add or select.
         */
        public void addOrSelectProgram(final List<String> code) {
            if (code.isEmpty()) {
                return;
            }

            final List<List<String>> newPages = new ArrayList<>();

            final List<String> page = new ArrayList<>();
            for (int i = 0; i < code.size(); i++) {
                final String line = code.get(i);
                page.add(line);

                if (Objects.equals(line, CONTINUATION_MACRO)) {
                    newPages.add(new ArrayList<>(page));
                    page.clear();
                } else if (page.size() == Constants.MAX_LINES_PER_PAGE) {
                    final boolean isLastPage = i + 1 == code.size();
                    if (!isLastPage && !isPartialProgram(page)) {
                        page.set(page.size() - 1, CONTINUATION_MACRO);
                        newPages.add(new ArrayList<>(page));
                        page.clear();
                        page.add(line);
                    } else {
                        newPages.add(new ArrayList<>(page));
                        page.clear();
                    }
                }
            }
            if (page.size() > 0) {
                newPages.add(page);
            }

            for (int startPage = 0; startPage < pages.size(); startPage++) {
                if (areAllPagesEqual(newPages, startPage)) {
                    setSelectedPage(startPage);
                    return;
                }
            }

            pages.addAll(newPages);
            setSelectedPage(pages.size() - newPages.size());
        }

        /**
         * Overwrite a page at the specified index.
         *
         * @param page the index of the page to overwrite.
         * @param code the code of the page.
         */
        public void setPage(final int page, final List<String> code) {
            pages.set(page, new ArrayList<>(code));
        }

        /**
         * Remove a page from the book.
         *
         * @param index the index of the page to remove.
         */
        public void removePage(final int index) {
            pages.remove(index);
            validateSelectedPage();
        }

        /**
         * Get the complete program of the selected page, taking into account the
         * <code>#BWTM</code> preprocessor macro allowing programs to span multiple pages.
         *
         * @return the full program starting on the current page.
         */
        public List<String> getProgram() {
            final List<String> program = new ArrayList<>(getPage(getSelectedPage()));
            final List<String> leadingCode = new ArrayList<>();
            final List<String> trailingCode = new ArrayList<>();
            getExtendedProgram(getSelectedPage(), program, leadingCode, trailingCode);
            program.addAll(0, leadingCode);
            program.addAll(trailingCode);
            return program;
        }

        /**
         * Get the leading and trailing code lines of a program spanning the specified
         * page, taking into account the <code>#BWTM</code> preprocessor marco. This
         * assumes <em>that the specified page does have the <code>#BWTM</code>
         * preprocessor macro</em>. I.e. the next page will <em>always</em> be added to
         * the <code>trailingPages</code>.
         *
         * @param page         the page to extend from.
         * @param program      the code on the page to extend from.
         * @param leadingCode  the list to place code from previous pages into.
         * @param trailingCode the list to place code from next pages into.
         */
        public void getExtendedProgram(final int page, final List<String> program, final List<String> leadingCode, final List<String> trailingCode) {
            for (int leadingPage = page - 1; leadingPage >= 0; leadingPage--) {
                final List<String> pageCode = getPage(leadingPage);
                if (isPartialProgram(pageCode)) {
                    leadingCode.addAll(0, pageCode);
                } else {
                    break;
                }
            }
            if (isPartialProgram(program)) {
                for (int trailingPage = page + 1; trailingPage < getPageCount(); trailingPage++) {
                    final List<String> pageCode = getPage(trailingPage);
                    trailingCode.addAll(pageCode);
                    if (!isPartialProgram(pageCode)) {
                        break;
                    }
                }
            }
        }

        /**
         * Check if this program continues on the next page, i.e. if the last
         * non-whitespace line has the <code>#BWTM</code> preprocessor macro.
         *
         * @param program the program to check for.
         * @return <code>true</code> if the program continues; <code>false</code> otherwise.
         */
        public static boolean isPartialProgram(final List<String> program) {
            boolean continues = false;
            for (final String line : program) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                continues = Objects.equals(line.trim().toUpperCase(Locale.US), CONTINUATION_MACRO);
            }
            return continues;
        }

        /**
         * Load data from the specified NBT tag.
         *
         * @param nbt the tag to load the data from.
         */
        public void readFromNBT(final NBTTagCompound nbt) {
            pages.clear();

            final NBTTagList pagesNbt = nbt.getTagList(TAG_PAGES, net.minecraftforge.common.util.Constants.NBT.TAG_STRING);
            for (int index = 0; index < pagesNbt.tagCount(); index++) {
                pages.add(Arrays.asList(Constants.PATTERN_LINES.split(pagesNbt.getStringTagAt(index))));
            }

            selectedPage = nbt.getInteger(TAG_SELECTED);
            validateSelectedPage();
        }

        /**
         * Store the data to the specified NBT tag.
         *
         * @param nbt the tag to save the data to.
         */
        public void writeToNBT(final NBTTagCompound nbt) {
            final NBTTagList pagesNbt = new NBTTagList();
            int removed = 0;
            for (int index = 0; index < pages.size(); index++) {
                final List<String> program = pages.get(index);
                if (program.size() > 1 || program.get(0).length() > 0) {
                    pagesNbt.appendTag(new NBTTagString(String.join("\n", program)));
                } else if (index < selectedPage) {
                    removed++;
                }
            }
            nbt.setTag(TAG_PAGES, pagesNbt);

            nbt.setInteger(TAG_SELECTED, selectedPage - removed);
        }

        // --------------------------------------------------------------------- //

        private void validateSelectedPage() {
            selectedPage = Math.max(0, Math.min(pages.size() - 1, selectedPage));
        }

        private boolean areAllPagesEqual(final List<List<String>> newPages, final int startPage) {
            for (int offset = 0; offset < newPages.size(); offset++) {
                final List<String> have = pages.get(startPage + offset);
                final List<String> want = newPages.get(offset);
                if (!Objects.equals(have, want)) {
                    return false;
                }
            }

            return true;
        }

        // --------------------------------------------------------------------- //

        /**
         * Load code book data from the specified NBT tag.
         *
         * @param nbt the tag to load the data from.
         * @return the data loaded from the tag.
         */
        public static Data loadFromNBT(@Nullable final NBTTagCompound nbt) {
            final Data data = new Data();
            if (nbt != null) {
                data.readFromNBT(nbt);
            }
            return data;
        }

        /**
         * Load code book data from the specified item stack.
         *
         * @param stack the item stack to load the data from.
         * @return the data loaded from the stack.
         */
        public static Data loadFromStack(final ItemStack stack) {
            return loadFromNBT(stack.getTagCompound());
        }

        /**
         * Save the specified code book data to the specified item stack.
         *
         * @param stack the item stack to save the data to.
         * @param data  the data to save to the item stack.
         */
        public static void saveToStack(final ItemStack stack, final Data data) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt == null) {
                stack.setTagCompound(nbt = new NBTTagCompound());
            }
            data.writeToNBT(nbt);
        }
    }
}
