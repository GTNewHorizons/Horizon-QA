package com.gtnewhorizons.horizonqa.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.horizonqa.item.ItemHorizonWand;
import com.gtnewhorizons.horizonqa.network.HorizonQANetwork;
import com.gtnewhorizons.horizonqa.network.WandLabelMessage;

public final class WandLabelPrompt extends GuiScreen {

    private static final int BUTTON_REMOVE = 1;

    private final int x;
    private final int y;
    private final int z;
    private final String existingName;
    private GuiTextField input;
    private String error = "";

    WandLabelPrompt(int x, int y, int z, String existingName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.existingName = existingName;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int fieldWidth = 180;
        input = new GuiTextField(fontRendererObj, (width - fieldWidth) / 2, height / 2 - 6, fieldWidth, 20);
        input.setMaxStringLength(64);
        input.setFocused(true);
        input.setText(existingName != null ? existingName : "");
        if (existingName != null) {
            buttonList.add(new GuiButton(BUTTON_REMOVE, (width - 80) / 2, height / 2 + 38, 80, 20, "Remove"));
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            Minecraft.getMinecraft()
                .displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submit();
            return;
        }
        input.textboxKeyTyped(typedChar, keyCode);
        validateCurrentName();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_REMOVE && existingName != null) {
            HorizonQANetwork.CHANNEL.sendToServer(new WandLabelMessage(existingName, x, y, z, true));
            Minecraft.getMinecraft()
                .displayGuiScreen(null);
        }
    }

    @Override
    public void updateScreen() {
        input.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = existingName != null ? "Rename label" : "Create label";
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 34, 0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            "Target: " + x + ", " + y + ", " + z,
            width / 2,
            height / 2 - 22,
            0xAAAAAA);
        input.drawTextBox();
        if (!error.isEmpty()) {
            drawCenteredString(fontRendererObj, error, width / 2, height / 2 + 20, 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void submit() {
        String name = input.getText()
            .trim();
        if (!ItemHorizonWand.isValidLabelName(name)) {
            error = "Use [A-Za-z_][A-Za-z0-9_]*";
            return;
        }
        HorizonQANetwork.CHANNEL.sendToServer(new WandLabelMessage(name, x, y, z));
        Minecraft.getMinecraft()
            .displayGuiScreen(null);
    }

    private void validateCurrentName() {
        String name = input.getText()
            .trim();
        error = name.isEmpty() || ItemHorizonWand.isValidLabelName(name) ? "" : "Use [A-Za-z_][A-Za-z0-9_]*";
    }
}
