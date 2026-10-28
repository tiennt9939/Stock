package com.rollgarden.stock;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.tasks.Tasks;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int FILE_PICKER = 4101;
    private static final long MAX_PDF_BYTES = 25L * 1024L * 1024L;
    private static final int MAX_PDF_PAGES = 80;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private volatile Uri selectedPdfUri;
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 87, 61));
        getWindow().setNavigationBarColor(Color.rgb(6, 74, 53));
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(244, 248, 245));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.setType("application/pdf");
                    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(pick, FILE_PICKER);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Không mở được trình chọn PDF", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_PICKER || filePathCallback == null) return;
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        selectedPdfUri = uri;
        filePathCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
        filePathCallback = null;
    }

    private void js(String code) {
        runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(code, null); });
    }
    private String quote(String s) { return JSONObject.quote(s == null ? "" : s); }
    private String safeCallback(String callback, String fallback) {
        return callback != null && callback.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? callback : fallback;
    }

    public class AndroidBridge {
        @JavascriptInterface public void ocrSelectedPdf(String callback) {
            final String cb = safeCallback(callback, "__rgsPdfOcrResult");
            final Uri uri = selectedPdfUri;
            if (uri == null) {
                js("if(window[" + quote(cb) + "])window[" + quote(cb) + "]({error:'Chưa có PDF được chọn. Hãy chọn lại tệp.'});");
                return;
            }
            pdfExecutor.execute(() -> processPdf(uri, cb));
        }

        private void processPdf(Uri uri, String cb) {
            // Packing List PDFs commonly contain selectable text. Read that text directly first
            // so Order IDs, plant names, size tokens, combo labels and Qty are not corrupted by OCR.
            if (tryExtractPdfText(uri, cb)) return;
            processPdfWithOcr(uri, cb);
        }

        private boolean tryExtractPdfText(Uri uri, String cb) {
            ParcelFileDescriptor check = null;
            InputStream input = null;
            PDDocument document = null;
            try {
                check = getContentResolver().openFileDescriptor(uri, "r");
                if (check == null || check.getStatSize() > MAX_PDF_BYTES) return false;
                check.close();
                check = null;

                PDFBoxResourceLoader.init(getApplicationContext());
                input = getContentResolver().openInputStream(uri);
                if (input == null) return false;
                document = PDDocument.load(input);
                int count = document.getNumberOfPages();
                if (count < 1 || count > MAX_PDF_PAGES) return false;

                JSONArray pages = new JSONArray();
                int totalChars = 0;
                int pagesWithOrderId = 0;
                for (int i = 1; i <= count; i++) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(document);
                    if (pageText == null) pageText = "";
                    totalChars += pageText.length();
                    if (pageText.matches("(?is).*\\b(?:Order\\s*ID|Order\\s*(?:No|Number))\\b.*\\d{10,}.*")
                            || pageText.matches("(?s).*\\b\\d{18}\\b.*")) pagesWithOrderId++;

                    JSONObject page = new JSONObject();
                    page.put("page", i);
                    page.put("text", pageText);
                    pages.put(page);
                    final int done = i;
                    js("if(window.__rgsPdfOcrProgress)window.__rgsPdfOcrProgress(" + done + "," + count + ");");
                }

                // Refuse a mostly empty text layer; processPdf will then try on-device OCR.
                int minimumText = Math.max(80, count * 30);
                int minimumIdPages = Math.max(1, (count * 2) / 3);
                if (totalChars < minimumText || pagesWithOrderId < minimumIdPages) return false;

                JSONObject result = new JSONObject();
                result.put("pages", pages);
                js("if(window[" + quote(cb) + "])window[" + quote(cb) + "](" + result.toString() + ");");
                return true;
            } catch (Exception ignored) {
                // Scanned/encrypted/unusual PDFs fall back to ML Kit OCR below.
                return false;
            } finally {
                try { if (document != null) document.close(); } catch (Exception ignored) {}
                try { if (input != null) input.close(); } catch (Exception ignored) {}
                try { if (check != null) check.close(); } catch (Exception ignored) {}
            }
        }

        private void processPdfWithOcr(Uri uri, String cb) {
            ParcelFileDescriptor pfd = null;
            PdfRenderer renderer = null;
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            try {
                pfd = getContentResolver().openFileDescriptor(uri, "r");
                if (pfd == null) throw new Exception("Không mở được tệp PDF đã chọn.");
                long fileSize = pfd.getStatSize();
                if (fileSize > MAX_PDF_BYTES) throw new Exception("PDF lớn hơn 25 MB. Hãy tách tệp thành phần nhỏ hơn.");
                renderer = new PdfRenderer(pfd);
                int count = renderer.getPageCount();
                if (count < 1) throw new Exception("PDF không có trang.");
                if (count > MAX_PDF_PAGES) throw new Exception("PDF có " + count + " trang, vượt giới hạn 80 trang. Hãy tách tệp rồi nhập từng phần.");
                JSONArray pages = new JSONArray();
                for (int i = 0; i < count; i++) {
                    PdfRenderer.Page page = renderer.openPage(i);
                    Bitmap bitmap = null;
                    try {
                        int width = page.getWidth(), height = page.getHeight();
                        float scale = Math.min(2.6f, 1800f / Math.max(width, height));
                        scale = Math.max(1.2f, scale);
                        int bw = Math.max(1, Math.round(width * scale));
                        int bh = Math.max(1, Math.round(height * scale));
                        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        Text recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 90, TimeUnit.SECONDS);
                        JSONObject p = new JSONObject();
                        p.put("page", i + 1);
                        p.put("text", recognized.getText() == null ? "" : recognized.getText());
                        p.put("imageWidth", bw);
                        p.put("imageHeight", bh);
                        JSONArray elementArray = new JSONArray();
                        for (Text.TextBlock block : recognized.getTextBlocks()) {
                            for (Text.Line textLine : block.getLines()) {
                                for (Text.Element element : textLine.getElements()) {
                                    Rect box = element.getBoundingBox();
                                    if (box == null) continue;
                                    JSONObject word = new JSONObject();
                                    word.put("text", element.getText() == null ? "" : element.getText());
                                    word.put("x", box.left / (double) bw);
                                    word.put("y", box.top / (double) bh);
                                    word.put("w", (box.right - box.left) / (double) bw);
                                    word.put("h", (box.bottom - box.top) / (double) bh);
                                    elementArray.put(word);
                                }
                            }
                        }
                        p.put("elements", elementArray);
                        pages.put(p);
                    } finally {
                        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                        page.close();
                    }
                    final int done = i + 1;
                    js("if(window.__rgsPdfOcrProgress)window.__rgsPdfOcrProgress(" + done + "," + count + ");");
                }
                JSONObject result = new JSONObject(); result.put("pages", pages);
                js("if(window[" + quote(cb) + "])window[" + quote(cb) + "](" + result.toString() + ");");
            } catch (Exception e) {
                try { JSONObject error = new JSONObject(); error.put("error", e.getMessage() == null ? "Không thể đọc PDF" : e.getMessage()); js("if(window[" + quote(cb) + "])window[" + quote(cb) + "](" + error.toString() + ");"); }
                catch (Exception ignored) { js("if(window.__rgsPdfOcrResult)window.__rgsPdfOcrResult({error:'Không thể đọc PDF'});"); }
            } finally {
                try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
                try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
                try { recognizer.close(); } catch (Exception ignored) {}
            }
        }

        @JavascriptInterface public void saveTextFile(String name, String base64) {
            try {
                String safe = (name == null ? "roll_garden_stock.csv" : name).replaceAll("[^A-Za-z0-9._-]", "_");
                if (safe.length() < 3) safe = "roll_garden_stock.csv";
                byte[] bytes = Base64.decode(base64 == null ? "" : base64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                    values.put(MediaStore.Downloads.MIME_TYPE, safe.endsWith(".json") ? "application/json" : "text/csv");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ROLL GARDEN STOCK");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Không tạo được tệp trong thư mục Downloads");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out == null) throw new Exception("Không mở được tệp để ghi"); out.write(bytes); out.flush(); }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); getContentResolver().update(uri, values, null, null);
                    toast("Đã lưu vào Tải xuống/ROLL GARDEN STOCK: " + safe);
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) dir = getFilesDir();
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục lưu");
                    File target = new File(dir, safe);
                    try (FileOutputStream out = new FileOutputStream(target)) { out.write(bytes); out.flush(); }
                    toast("Đã lưu tệp: " + target.getAbsolutePath());
                }
            } catch (Exception e) { toast("Không lưu được tệp: " + (e.getMessage() == null ? "lỗi ghi tệp" : e.getMessage())); }
        }
        private void toast(String message) { runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show()); }
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.stopLoading(); webView.removeJavascriptInterface("AndroidBridge"); webView.destroy(); webView = null; }
        pdfExecutor.shutdownNow();
        super.onDestroy();
    }
}
