package gg.earthme.cyanidin.cyanidin.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class I18NManager {
    private final Map<String,String> loadedLanguageKeys = new ConcurrentHashMap<>();

    public void loadLanguageFile(String languageName) throws IOException {
        final InputStream languageFileInStream = this.getClass().getClassLoader().getResourceAsStream("lang/" + languageName + ".lang");

        if (languageFileInStream == null) throw new IOException("Language file not found for " + languageName + "!");

        final InputStreamReader languageFileReader = new InputStreamReader(languageFileInStream);
        final BufferedReader lineReader = new BufferedReader(languageFileReader);

        String languageLine;
        while (((languageLine = lineReader.readLine()) != null)){
            final String[] languageLineSplit = languageLine.split("=");

            if (languageLineSplit.length == 2){
                this.loadedLanguageKeys.put(languageLineSplit[0], languageLineSplit[1]);
                continue;
            }

            throw new IllegalArgumentException("Invalid language file format " + languageLine + "!");
        }
    }

    public Component i18n(String key, @NotNull List<String> subKeys, @NotNull List<Object> args){
        if (subKeys.size() != args.size()){
            throw new IllegalArgumentException("Subkeys and args must be the same length");
        }

        final List<TagResolver> builtResolvers = new ArrayList<>();

        int idx = 0;
        for (Object arg : args){
            builtResolvers.add(
                    Placeholder
                            .component(subKeys.get(idx), arg instanceof Component ? (Component) arg : Component.text(String.valueOf(arg)))
            );
            idx++;
        }

        return MiniMessage.miniMessage().deserialize(this.loadedLanguageKeys.get(key), builtResolvers.toArray(TagResolver[]::new));
    }
}
