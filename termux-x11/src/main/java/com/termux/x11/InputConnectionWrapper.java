package com.termux.x11;

import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.PreviewableHandwritingGesture;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextAttribute;
import android.view.inputmethod.TextBoundsInfoResult;
import android.view.inputmethod.TextSnapshot;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class InputConnectionWrapper implements InputConnection {
    private static final String TAG = "InputConnectionWrapper";
    private final InputConnection wrapped;

    public InputConnectionWrapper(InputConnection wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public CharSequence getTextBeforeCursor(int n, int flags) {
        Log.d(TAG, "getTextBeforeCursor(" + n + ", " + flags + ")");
        return wrapped.getTextBeforeCursor(n, flags);
    }

    @Override
    public CharSequence getTextAfterCursor(int n, int flags) {
        Log.d(TAG, "getTextAfterCursor(" + n + ", " + flags + ")");
        return wrapped.getTextAfterCursor(n, flags);
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        Log.d(TAG, "getSelectedText(" + flags + ")");
        return wrapped.getSelectedText(flags);
    }

    @Override
    public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        Log.d(TAG, "getSurroundingText(" + beforeLength + ", " + afterLength + ", " + flags + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return wrapped.getSurroundingText(beforeLength, afterLength, flags);
        } else return null;
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        Log.d(TAG, "getCursorCapsMode(" + reqModes + ")");
        return wrapped.getCursorCapsMode(reqModes);
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        Log.d(TAG, "getExtractedText(" + request + ", " + flags + ")");
        return wrapped.getExtractedText(request, flags);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        Log.d(TAG, "deleteSurroundingText(" + beforeLength + ", " + afterLength + ")");
        return wrapped.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        Log.d(TAG, "deleteSurroundingTextInCodePoints(" + beforeLength + ", " + afterLength + ")");
        return wrapped.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        Log.d(TAG, "setComposingText(" + text + ", " + newCursorPosition + ")");
        return wrapped.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingText(@NonNull CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        Log.d(TAG, "setComposingText(" + text + ", " + newCursorPosition + ", " + textAttribute + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return wrapped.setComposingText(text, newCursorPosition, textAttribute);
        } else return false;
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        Log.d(TAG, "setComposingRegion(" + start + ", " + end + ")");
        return wrapped.setComposingRegion(start, end);
    }

    @Override
    public boolean setComposingRegion(int start, int end, TextAttribute textAttribute) {
        Log.d(TAG, "setComposingRegion(" + start + ", " + end + ", " + textAttribute + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return wrapped.setComposingRegion(start, end, textAttribute);
        } else return false;
    }

    @Override
    public boolean finishComposingText() {
        Log.d(TAG, "finishComposingText()");
        return wrapped.finishComposingText();
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        Log.d(TAG, "commitText(" + text + ", " + newCursorPosition + ")");
        return wrapped.commitText(text, newCursorPosition);
    }

    @Override
    public boolean commitText(@NonNull CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        Log.d(TAG, "commitText(" + text + ", " + newCursorPosition + ", " + textAttribute + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return wrapped.commitText(text, newCursorPosition, textAttribute);
        } else return false;
    }

    @Override
    public boolean commitCompletion(CompletionInfo text) {
        Log.d(TAG, "commitCompletion(" + text + ")");
        return wrapped.commitCompletion(text);
    }

    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        Log.d(TAG, "commitCorrection(" + correctionInfo + ")");
        return wrapped.commitCorrection(correctionInfo);
    }

    @Override
    public boolean setSelection(int start, int end) {
        Log.d(TAG, "setSelection(" + start + ", " + end + ")");
        return wrapped.setSelection(start, end);
    }

    @Override
    public boolean performEditorAction(int editorAction) {
        Log.d(TAG, "performEditorAction(" + editorAction + ")");
        return wrapped.performEditorAction(editorAction);
    }

    @Override
    public boolean performContextMenuAction(int id) {
        Log.d(TAG, "performContextMenuAction(" + id + ")");
        return wrapped.performContextMenuAction(id);
    }

    @Override
    public boolean beginBatchEdit() {
        Log.d(TAG, "beginBatchEdit()");
        return wrapped.beginBatchEdit();
    }

    @Override
    public boolean endBatchEdit() {
        Log.d(TAG, "endBatchEdit()");
        return wrapped.endBatchEdit();
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        Log.d(TAG, "sendKeyEvent(" + event + ")");
        return wrapped.sendKeyEvent(event);
    }

    @Override
    public boolean clearMetaKeyStates(int states) {
        Log.d(TAG, "clearMetaKeyStates(" + states + ")");
        return wrapped.clearMetaKeyStates(states);
    }

    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        Log.d(TAG, "reportFullscreenMode(" + enabled + ")");
        return wrapped.reportFullscreenMode(enabled);
    }

    @Override
    public boolean performSpellCheck() {
        Log.d(TAG, "performSpellCheck()");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return wrapped.performSpellCheck();
        } else return false;
    }

    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        Log.d(TAG, "performPrivateCommand(" + action + ", " + data + ")");
        return wrapped.performPrivateCommand(action, data);
    }

    @Override
    public void performHandwritingGesture(@NonNull HandwritingGesture gesture, Executor executor, IntConsumer consumer) {
        Log.d(TAG, "performHandwritingGesture(" + gesture + ", " + executor + ", " + consumer + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            wrapped.performHandwritingGesture(gesture, executor, consumer);
        }
    }

    @Override
    public boolean previewHandwritingGesture(@NonNull PreviewableHandwritingGesture gesture, CancellationSignal cancellationSignal) {
        Log.d(TAG, "previewHandwritingGesture(" + gesture + ", " + cancellationSignal + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return wrapped.previewHandwritingGesture(gesture, cancellationSignal);
        } else return false;
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        Log.d(TAG, "requestCursorUpdates(" + cursorUpdateMode + ")");
        return wrapped.requestCursorUpdates(cursorUpdateMode);
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
        Log.d(TAG, "requestCursorUpdates(" + cursorUpdateMode + ", " + cursorUpdateFilter + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return wrapped.requestCursorUpdates(cursorUpdateMode, cursorUpdateFilter);
        } else return false;
    }

    @Override
    public void requestTextBoundsInfo(@NonNull RectF bounds, @NonNull Executor executor, @NonNull Consumer<TextBoundsInfoResult> consumer) {
        Log.d(TAG, "requestTextBoundsInfo(" + bounds + ", " + executor + ", " + consumer + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            wrapped.requestTextBoundsInfo(bounds, executor, consumer);
        }
    }

    @Override
    public Handler getHandler() {
        Log.d(TAG, "getHandler()");
        return wrapped.getHandler();
    }

    @Override
    public void closeConnection() {
        Log.d(TAG, "closeConnection()");
        wrapped.closeConnection();
    }

    @Override
    public boolean commitContent(@NonNull InputContentInfo inputContentInfo, int flags, Bundle opts) {
        Log.d(TAG, "commitContent(" + inputContentInfo + ", " + flags + ", " + opts + ")");
        return wrapped.commitContent(inputContentInfo, flags, opts);
    }

    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        Log.d(TAG, "setImeConsumesInput(" + imeConsumesInput + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return wrapped.setImeConsumesInput(imeConsumesInput);
        } else return false;
    }

    @Override
    public TextSnapshot takeSnapshot() {
        Log.d(TAG, "takeSnapshot()");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return wrapped.takeSnapshot();
        } else return null;
    }

    @Override
    public boolean replaceText(int start,
                               int end,
                               @NonNull CharSequence text,
                               int newCursorPosition,
                               TextAttribute textAttribute) {
        Log.d(TAG, "replaceText(" + start + ", " + end + ", " + text + ", " + newCursorPosition + ", " + textAttribute + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return wrapped.replaceText(start, end, text, newCursorPosition, textAttribute);
        } else return false;
    }
}

