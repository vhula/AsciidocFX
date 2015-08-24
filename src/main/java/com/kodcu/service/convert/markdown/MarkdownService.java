package com.kodcu.service.convert.markdown;

import com.kodcu.other.Current;
import com.kodcu.service.ThreadService;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Created by usta on 04.03.2015.
 */
@Lazy
@Component
public class MarkdownService {

    private final Logger logger = LoggerFactory.getLogger(MarkdownService.class);

    private final Current current;
    private final ThreadService threadService;
    private final ScriptEngine scriptEngine;
    private final CompletableFuture completableFuture = new CompletableFuture();

    @Autowired
    public MarkdownService(Current current, ThreadService threadService, ScriptEngine scriptEngine) {
        this.current = current;
        this.threadService = threadService;
        this.scriptEngine = scriptEngine;

        completableFuture.runAsync(() -> {
            try {
                List<String> scripts = Arrays.asList("marked.js", "marked-extension.js");

                for (String script : scripts) {
                    try (InputStream inputStream = MarkdownService.class.getResourceAsStream("/public/js/" + script);
                         InputStreamReader inputStreamReader = new InputStreamReader(inputStream);) {
                        scriptEngine.eval(inputStreamReader);
                    }
                }
                completableFuture.complete(null);
            } catch (Exception e) {
                logger.error("Could not evaluate initial javascript", e);
                completableFuture.complete(e);
            }
        }, threadService.executor());
    }

    public void convertToAsciidoc(String content, Consumer<String>... next) {

        threadService.runTaskLater(() -> {

            if (Objects.isNull(content))
                return;

            completableFuture.join();

            Object eval = "";
            try {
                Invocable invocable = (Invocable) scriptEngine;
                eval = invocable.invokeFunction("markdownToAsciidoc", content);
            } catch (Exception e) {
                logger.error("Problem occured while converting Asciidoc to Markdown", e);
            } finally {
                for (Consumer<String> n : next) {
                    n.accept((String) eval);
                }
            }
        });

    }

}
