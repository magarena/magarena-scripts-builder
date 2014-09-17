/**
 *
 */
package mtgjson.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * Given a list of missing card names from Magarena this will attempt to match each
 * card name to an entry in the json feed from mgtjson.com and create a script file
 * for each matching card which can be loaded into MagicCardDefinition for display
 * in the Card Explorer screen.
 *
 */
public class MtgJsonReader {

    // This file is obtained from mtgjson.com. It contains every card grouped by set.
    private static final String JSON_FILE = "AllSets.json";

    // This is a list of the cards which have not yet been implemented in Magarena.
    // In effect it is the list of cards in AllCardNames.txt minus those cards which
    // have a matching script file in the "cards" folder.
    // !! NB I hacked some code in Magarena to create this (not supplied) !!
    private static final String MISSING_CARDS_FILE = "CardsMissingInMagarena.txt";

    // This contains entries from CardsMissingInMagarena.txt which have no matching entry in
    // the mtgcom json ("AllSets.json") file. Check the name for typos, strange characters, etc.
    private static final String MISSING_ORPHANS_FILE = "MissingCardOrphans.txt";

    private static final List<String> setCodesList = new ArrayList<String>();
    private static final HashMap<String, CardData> mtgcomCards = new HashMap<String, CardData>();
    private static final List<String> magarenaMissingCards = new ArrayList<String>();

    public static void main(String[] args) throws IOException {

        loadJsonData();
        System.out.println("Total unique cards identified in json feed = " + mtgcomCards.size() + " (see " + JSON_FILE + ")");

        loadMissingMagarenaCards();
        System.out.println("Total missing cards in Magarena = " + magarenaMissingCards.size() + " (see " + MISSING_CARDS_FILE + ")");

        // sort list of ALL card names from json file.
        final List<String> mtgcomCardNames = new ArrayList<String>(mtgcomCards.keySet());
        Collections.sort(mtgcomCardNames);

        final int missingOrphans = saveListOfMissingCardOrphans(mtgcomCardNames);
        System.out.println("Total missing cards which could not be matched in " + JSON_FILE + " = " + missingOrphans + " (see " + MISSING_ORPHANS_FILE + ")");

        mtgcomCardNames.retainAll(magarenaMissingCards);
        System.out.println("Total missing cards which have a matching entry in " + JSON_FILE + " = " + magarenaMissingCards.size() + "-" + missingOrphans + " = " + mtgcomCardNames.size());

        saveMissingCardData(mtgcomCardNames);
        System.out.println("Created a default script file for each missing card in \"scripts\" folder.");

        System.out.println("Finished.");

    }

    /**
     * Loads the data from JSON_FILE using google gson library.
     * <p>
     * The data is stored by card set so in order to get all cards you have to
     * step through each set and pick out any new unique cards.
     */
    private static void loadJsonData() throws IOException {

        // Explicitly state UTF-8 otherwise will get strange characters if the default
        // encoding is different (which it seems to be on Windows 7 at least).
        try (final BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(JSON_FILE), "UTF-8"))) {

            final JsonParser parser = new JsonParser();
            final JsonElement element = parser.parse(reader);

            for (String setCode : getSetCodes()) {
                // Not interested in unsets or vanguard.
                if (!setCode.equalsIgnoreCase("UNG") && !setCode.equalsIgnoreCase("UNH") && !setCode.equalsIgnoreCase("VAN")) {
                    final JsonObject setObject = element.getAsJsonObject().get(setCode).getAsJsonObject();
                    extractCardDataFromJson(setObject.getAsJsonArray("cards"));
                }
            }

        }

    }

    /**
     * Saves a list of the card names that are present in the missing cards list from
     * Magarena but which have no matching card name in the json file from mtgjson.com.
     */
    private static int saveListOfMissingCardOrphans(final List<String> mtgcomCardNames) {
        final List<String> missingCardOrphans = new ArrayList<String>(magarenaMissingCards);
        missingCardOrphans.removeAll(mtgcomCardNames);
        Collections.sort(missingCardOrphans);
        final File textFile = getResultsPath().resolve(MISSING_ORPHANS_FILE).toFile();
        try (final PrintWriter writer = new PrintWriter(textFile)) {
            for (String cardName : missingCardOrphans) {
                writer.println(cardName);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        };
        return missingCardOrphans.size();
    }

    private static void extractCardDataFromJson(final JsonArray cards) throws UnsupportedEncodingException {
        for (int i = 0; i < cards.size(); i++) {
            final CardData cardData = new CardData(cards.get(i).getAsJsonObject());
            if (!mtgcomCards.containsKey(cardData.getCardName(false)) && !cardData.getRarity().contentEquals("S")) {
                mtgcomCards.put(cardData.getCardName(false), cardData);
            };
        }
    }

    private static void saveMissingCardData(final List<String> cardNames) {
        FileUtils.deleteQuietly(getScriptsPath().toFile());
        for (String cardName : cardNames) {
            final CardData cardData = mtgcomCards.get(cardName);
            saveCardData(cardData);
        }
    }

    private static void saveCardData(final CardData cardData) {

        // ensure unix style line endings.
        System.setProperty("line.separator", "\n");

        final Path filePath = getScriptsPath().resolve(cardData.getFilename());
        try (final PrintWriter writer = new PrintWriter(filePath.toString(), "UTF-8")) {
            writer.println("name=" + cardData.getCardName(false));
            writer.println("image=" + cardData.getImageUrl());
            writer.println("value=2.500");
            writer.println("rarity=" + cardData.getRarity().replace("S", "R"));
            writer.println("type=" + cardData.getType());
            if (cardData.getSubType() != null) {
                writer.println("subtype=" + cardData.getSubType());
            }
            if (cardData.getManaCost() == null && cardData.getColor() !=null) {
                writer.println("color="+cardData.getColor());
            }
            if (cardData.getManaCost() != null) {
                writer.println("cost=" + cardData.getManaCost());
            }
            if (cardData.getPower() != null && cardData.getToughness() != null) {
                writer.println("pt=" + cardData.getPower() + "/" + cardData.getToughness());
            }
            if (cardData.getText() != null) {
                if (cardData.getEffectText() !=null) { writer.println("effect=" + cardData.getEffectText()); }
                else if (cardData.getAbilityText() !=null) { writer.println("ability="+cardData.getAbilityText()); }
            }
            writer.println("timing=" + cardData.getTiming());            
            if (cardData.getText() !=null) { writer.println("oracle="+cardData.getOracleText()); }
            
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        };
    }

    private static String[] getSetCodes() throws IOException {
        FileReader fileReader = new FileReader(JSON_FILE);
        JsonReader reader = new JsonReader(fileReader);
        handleObject(reader);
        return setCodesList.toArray(new String[setCodesList.size()]);
    }

    /**
     * Handle an Object. Consume the first token which is BEGIN_OBJECT. Within
     * the Object there could be array or non array tokens. We write handler
     * methods for both. Noe the peek() method. It is used to find out the type
     * of the next token without actually consuming it.
     *
     * @param reader
     * @throws IOException
     */
    private static void handleObject(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            JsonToken token = reader.peek();
            if (token.equals(JsonToken.BEGIN_ARRAY))
                handleArray(reader);
            else if (token.equals(JsonToken.END_ARRAY)) {
                reader.endObject();
                return;
            } else
                handleNonArrayToken(reader, token);
        }

    }

    /**
     * Handle a json array. The first token would be JsonToken.BEGIN_ARRAY.
     * Arrays may contain objects or primitives.
     *
     * @param reader
     * @throws IOException
     */
    public static void handleArray(JsonReader reader) throws IOException {
        reader.beginArray();
        while (true) {
            JsonToken token = reader.peek();
            if (token.equals(JsonToken.END_ARRAY)) {
                reader.endArray();
                break;
            } else if (token.equals(JsonToken.BEGIN_OBJECT)) {
                handleObject(reader);
            } else
                handleNonArrayToken(reader, token);
        }
    }

    /**
     * Handle non array non object tokens
     *
     * @param reader
     * @param token
     * @throws IOException
     */
    public static void handleNonArrayToken(JsonReader reader, JsonToken token) throws IOException {
        if (token.equals(JsonToken.NAME)) {
            final String value = reader.nextName();
            setCodesList.add(value);
        } else if (token.equals(JsonToken.STRING)) {
            System.out.println(reader.nextString());
        } else if (token.equals(JsonToken.NUMBER)) {
            System.out.println(reader.nextDouble());
        } else {
            reader.skipValue();
        }
    }

    /**
     *  sorted list of missing card names from Magarena.
     */
    public static void loadMissingMagarenaCards() {
        magarenaMissingCards.clear();
        try {
            final Path filePath = Paths.get(MISSING_CARDS_FILE);
            for (final String cardName : Files.readAllLines(filePath, Charset.defaultCharset())) {
                magarenaMissingCards.add(cardName.trim());
            }
        } catch (final IOException ex) {
           throw new RuntimeException(ex);
        }
    }

    private static Path getResultsPath() {
        final Path resultsPath = Paths.get("results");
        if (!Files.isDirectory(resultsPath)) {
            try {
                Files.createDirectory(resultsPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return resultsPath;
    }

    private static Path getScriptsPath() {
        final Path scriptsPath = getResultsPath().resolve("scripts");
        if (!Files.isDirectory(scriptsPath)) {
            try {
                Files.createDirectory(scriptsPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return scriptsPath;
    }

}
