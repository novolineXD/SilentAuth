package dev.silentauth.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * A single JSON document on disk. Writes go to a sibling temporary file first and are only
 * moved into place once they are complete, so a crash halfway through can never leave a
 * half written accounts file behind.
 */
public final class JsonStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final String description;

    public JsonStore(File file, String description) {
        this.file = file;
        this.description = description;
    }

    public synchronized JsonObject read() {
        if (!file.isFile()) {
            return new JsonObject();
        }
        try {
            return Json.parseObject(readText(file));
        } catch (IOException e) {
            Log.error("Could not read " + description, e);
            return new JsonObject();
        }
    }

    public synchronized void write(JsonObject root) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            Log.error("Could not create " + parent, null);
            return;
        }
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), "UTF-8");
            try {
                GSON.toJson(root, writer);
                writer.flush();
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            Log.error("Could not write " + description, e);
            temporary.delete();
            return;
        }

        // Windows will not rename onto an existing file, so the old copy goes first.
        if (file.isFile() && !file.delete()) {
            Log.warn("Could not replace " + description + ", keeping the previous copy");
            temporary.delete();
            return;
        }
        if (!temporary.renameTo(file)) {
            Log.warn("Could not move the new " + description + " into place");
            return;
        }
        ownerOnly(file);
    }

    /** Best effort: strip group and world access so tokens are not world readable. */
    static void ownerOnly(File file) {
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
    }

    static String readText(File file) throws IOException {
        Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
        try {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }
}
