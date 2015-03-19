package mtgjson.reader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
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
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.commons.io.FileUtils;

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

    private static final String JSON_SETS_FILE = "JsonSetCodes.txt";
    private static final String ERRORS_FILE = "errors.txt";

    private static final List<String> setCodesList = new ArrayList<>();
    private static final HashMap<String, CardData> mtgcomCards = new HashMap<>();
    private static final List<String> magarenaMissingCards = new ArrayList<>();
    private static final HashMap<String, String> mtginfoSetsMap = new HashMap<>();

    public static void main(String[] args) throws IOException {

        final long start_time = System.currentTimeMillis();
        System.out.println("Running...");

        loadJsonData();
        System.out.printf("Total unique cards identified in json feed = %d (see %s).\n",
                mtgcomCards.size(), JSON_FILE);

        loadMissingMagarenaCards();
        System.out.printf("Total missing cards in Magarena = %d (see %s).\n",
                magarenaMissingCards.size(), MISSING_CARDS_FILE);

        // sort list of ALL card names from json file.
        final List<String> mtgcomCardNames = new ArrayList<>(mtgcomCards.keySet());
        Collections.sort(mtgcomCardNames);

        final int missingOrphans = saveListOfMissingCardOrphans(mtgcomCardNames);
        System.out.printf("Total missing cards which could not be matched in %s = %d (see \\results\\%s).\n",
                JSON_FILE, missingOrphans, MISSING_ORPHANS_FILE);

        mtgcomCardNames.retainAll(magarenaMissingCards);
        System.out.printf("Total missing cards which have a matching entry in %s = %d-%d = %d\n",
                JSON_FILE, magarenaMissingCards.size(), missingOrphans, mtgcomCardNames.size());

        saveMissingCardData(mtgcomCardNames);
        System.out.printf("Created %d script files in \"\\results\\scripts\" folder.\n", mtgcomCardNames.size());

        logErrorDetails();

        final double duration = (double)(System.currentTimeMillis() - start_time) / 1000;
        System.out.printf("Finished in %.1f seconds.\n", duration);

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

            // list of set codes sorted by release date (map key) descending.
            final SortedMap<String, String> sortedSetCodes =
                    getSetCodesSortedByReleaseDateDesc(getSetCodes(), element);

            // save list of set codes for reference.
            logSetCodes(sortedSetCodes);

            loadMtgInfoSetsMap();

            for (Map.Entry<String, String> entrySet : sortedSetCodes.entrySet()) {
                final String jsonSetCode = entrySet.getValue();
                if (isValidSetCode(jsonSetCode)) {
                    final JsonObject jsonSetObject = element.getAsJsonObject().get(jsonSetCode).getAsJsonObject();
                    final String setCode = getSetCode(jsonSetCode);
                    extractCardDataFromJson(jsonSetObject.getAsJsonArray("cards"), setCode);
                }
            }

        }

    }

    private static void extractCardDataFromJson(final JsonArray cards, final String setCode) throws UnsupportedEncodingException {

        for (int i = 0; i < cards.size(); i++) {

            final JsonObject jsonCard = cards.get(i).getAsJsonObject();
            final String key = CardData.getRawCardName(jsonCard);

            if (!mtgcomCards.containsKey(key) && CardData.isValid(jsonCard)) {
                mtgcomCards.put(key, new CardData(cards.get(i).getAsJsonObject(), setCode));
            }
        }
    }

    
    private static String getSetCode(final String jsonSetCode) {
        final String key = jsonSetCode.toUpperCase().trim();
        final String code = mtginfoSetsMap.containsKey(key) ? mtginfoSetsMap.get(key) : jsonSetCode;
        return code;
    }

    private static void loadMtgInfoSetsMap() {
        // Base Sets
        mtginfoSetsMap.put("LEA", "al");
        mtginfoSetsMap.put("LEB", "be");
        mtginfoSetsMap.put("2ED", "un");
        mtginfoSetsMap.put("3ED", "rv");
        mtginfoSetsMap.put("4ED", "4e");
        mtginfoSetsMap.put("5ED", "5e");
        mtginfoSetsMap.put("6ED", "6e");
        mtginfoSetsMap.put("7ED", "7e");
        mtginfoSetsMap.put("8ED", "8e");
        mtginfoSetsMap.put("9ED", "9e");
        // Pre-Block sets
        mtginfoSetsMap.put("ARN", "an");
        mtginfoSetsMap.put("ATQ", "aq");
        mtginfoSetsMap.put("LEG", "lg");
        mtginfoSetsMap.put("DRK", "dk");
        mtginfoSetsMap.put("FEM", "fe");
        mtginfoSetsMap.put("HML", "hl");
        // Block sets
        mtginfoSetsMap.put("ICE", "ia");
        mtginfoSetsMap.put("ALL", "ai");
        mtginfoSetsMap.put("CSP", "cs");
        mtginfoSetsMap.put("MIR", "mr");
        mtginfoSetsMap.put("VIS", "vi");
        mtginfoSetsMap.put("WTH", "wl");
        mtginfoSetsMap.put("TMP", "tp");
        mtginfoSetsMap.put("STH", "sh");
        mtginfoSetsMap.put("EXO", "ex");
        mtginfoSetsMap.put("USG", "us");
        mtginfoSetsMap.put("ULG", "ul");
        mtginfoSetsMap.put("UDS", "ud");
        mtginfoSetsMap.put("MMQ", "mm");
        mtginfoSetsMap.put("NMS", "ne");
        mtginfoSetsMap.put("PCY", "pr");
        mtginfoSetsMap.put("INV", "in");
        mtginfoSetsMap.put("PLS", "ps");
        mtginfoSetsMap.put("APC", "ap");
        mtginfoSetsMap.put("ODY", "od");
        mtginfoSetsMap.put("TOR", "tr");
        mtginfoSetsMap.put("JUD", "ju");
        mtginfoSetsMap.put("ONS", "on");
        mtginfoSetsMap.put("LGN", "le");
        mtginfoSetsMap.put("SCG", "sc");
        mtginfoSetsMap.put("MRD", "mi");
        mtginfoSetsMap.put("DST", "ds");
        mtginfoSetsMap.put("GPT", "gp");
        mtginfoSetsMap.put("DIS", "di");
        mtginfoSetsMap.put("TSP", "ts");
        mtginfoSetsMap.put("TSB", "tsts");
        mtginfoSetsMap.put("PLC", "pc");
        mtginfoSetsMap.put("LRW", "lw");
        mtginfoSetsMap.put("MOR", "mt");
        mtginfoSetsMap.put("CON", "cfx");
        // Reprint Sets
        mtginfoSetsMap.put("CHR", "ch");
        mtginfoSetsMap.put("DD2", "jvc");
        mtginfoSetsMap.put("DDC", "dvd");
        mtginfoSetsMap.put("DDD", "gvl");
        mtginfoSetsMap.put("DDE", "pvc");
        mtginfoSetsMap.put("HOP", "pch");
        mtginfoSetsMap.put("CM1", "CMA");
        // Starter Sets
        mtginfoSetsMap.put("POR", "po");
        mtginfoSetsMap.put("PTK", "p3k");
        mtginfoSetsMap.put("S99", "st");
        mtginfoSetsMap.put("S00", "st2k");
        // Boxed Sets
        mtginfoSetsMap.put("BRB", "br");
        mtginfoSetsMap.put("BTD", "bd");
        mtginfoSetsMap.put("DKM", "dm");
        mtginfoSetsMap.put("ATH", "at");
        mtginfoSetsMap.put("MGB", "mgbc");
}

    private static void logErrorDetails() {
        final File textFile = getResultsPath().resolve(ERRORS_FILE).toFile();
        try (final PrintWriter writer = new PrintWriter(textFile)) {
            for (String error : CardData.cardImageErrors.values()) {
                writer.println(error);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (CardData.cardImageErrors.size() > 0) {
            System.out.printf("ERRORS = %d (see \\results\\%s)\n",
                    CardData.cardImageErrors.size(), ERRORS_FILE);
        } else {
            System.out.println("No errors generated.");
        }
    }

    private static void logSetCodes(final SortedMap<String, String> sortedSetCodes) {
        final File textFile = getResultsPath().resolve(JSON_SETS_FILE).toFile();
        try (final PrintWriter writer = new PrintWriter(textFile)) {
            for (Map.Entry<String, String> entrySet : sortedSetCodes.entrySet()) {
                final String key = entrySet.getKey();
                final String jsonSetCode = entrySet.getValue();
                final boolean isValidSetCode = isValidSetCode(jsonSetCode);
                final String setCode = getSetCode(jsonSetCode);
                if (isValidSetCode) {
                    if (!setCode.equalsIgnoreCase(jsonSetCode)) {
                        writer.printf("%s -> %s\n", key, setCode);
                    } else {
                        writer.printf("%s\n", key);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.printf("Processing %d sets in release date reverse order (see \\results\\%s)\n",
                sortedSetCodes.size(), JSON_SETS_FILE);
    }

    /**
     * Not interested in unsets or vanguard.
     */
    private static boolean isValidSetCode(final String setCode) {
        return !setCode.equalsIgnoreCase("UGL")
                && !setCode.equalsIgnoreCase("UNH")
                && !setCode.equalsIgnoreCase("VAN")
                // Foil sets
                && !setCode.equalsIgnoreCase("DRB")
                && !setCode.equalsIgnoreCase("V09")
                && !setCode.equalsIgnoreCase("V10")
                && !setCode.equalsIgnoreCase("V11")
                && !setCode.equalsIgnoreCase("V12")
                && !setCode.equalsIgnoreCase("V13")
                && !setCode.equalsIgnoreCase("V14")
                && !setCode.equalsIgnoreCase("V15")
                && !setCode.equalsIgnoreCase("H09")
                && !setCode.equalsIgnoreCase("PD2")
                && !setCode.equalsIgnoreCase("PD3")
                // Literal Re-prints
                && !setCode.equalsIgnoreCase("DD3_JVC")
                && !setCode.equalsIgnoreCase("DD3_GVL")
                && !setCode.equalsIgnoreCase("DD3_EVG")
                && !setCode.equalsIgnoreCase("DD3_DVD")
                // Not on magiccards.info
                && !setCode.equalsIgnoreCase("CST") // Cold Snap Theme deck reprints
                && !setCode.equalsIgnoreCase("DPA") // Duels of the Planeswalkers
                && !setCode.equalsIgnoreCase("RQS") // Rivals Quick Start Set
                && !setCode.equalsIgnoreCase("FRF_UGIN") // Ugin alternate art
                // Promo Cards (Normally foil or textless)
                && !setCode.equalsIgnoreCase("pWCQ")
                && !setCode.equalsIgnoreCase("p15A")
                && !setCode.equalsIgnoreCase("pLPA")
                && !setCode.equalsIgnoreCase("pSUM")
                && !setCode.equalsIgnoreCase("pMGD")
                && !setCode.equalsIgnoreCase("pGPX")
                && !setCode.equalsIgnoreCase("pPRO")
                && !setCode.equalsIgnoreCase("pHHO")
                && !setCode.equalsIgnoreCase("pCMP")
                && !setCode.equalsIgnoreCase("pWPN")
                && !setCode.equalsIgnoreCase("p2HG")
                && !setCode.equalsIgnoreCase("pREL")
                && !setCode.equalsIgnoreCase("pMPR")
                && !setCode.equalsIgnoreCase("pELP")
                && !setCode.equalsIgnoreCase("pFNM")
                && !setCode.equalsIgnoreCase("pSUS")
                && !setCode.equalsIgnoreCase("pWOS")
                && !setCode.equalsIgnoreCase("pWOR")
                && !setCode.equalsIgnoreCase("pGRU")
                && !setCode.equalsIgnoreCase("pALP")
                && !setCode.equalsIgnoreCase("pJGP")
                && !setCode.equalsIgnoreCase("pPRE")
                && !setCode.equalsIgnoreCase("pPOD")
                && !setCode.equalsIgnoreCase("pCEL")
                && !setCode.equalsIgnoreCase("pARL")
                && !setCode.equalsIgnoreCase("pMEI")
                && !setCode.equalsIgnoreCase("pLGM")
                && !setCode.equalsIgnoreCase("pDRC")
                ;
    }

    private static SortedMap<String, String> getSetCodesSortedByReleaseDateDesc(
            final String[] setCodes, final JsonElement element) {

        final SortedMap<String, String> sortedSetCodes =
                new TreeMap<>(Collections.reverseOrder());

        for (String setCode : setCodes) {
            final JsonObject setObject = element.getAsJsonObject().get(setCode).getAsJsonObject();
            final String setReleaseDate = setObject.get("releaseDate").getAsString();
            final String key = setReleaseDate + " " + setCode;
            sortedSetCodes.put(key, setCode);
        }
                
        return sortedSetCodes;
    }

    /**
     * Saves a list of the card names that are present in the missing cards list from
     * Magarena but which have no matching card name in the json file from mtgjson.com.
     */
    private static int saveListOfMissingCardOrphans(final List<String> mtgcomCardNames) {
        final List<String> missingCardOrphans = new ArrayList<>(magarenaMissingCards);
        missingCardOrphans.removeAll(mtgcomCardNames);
        Collections.sort(missingCardOrphans);
        final File textFile = getResultsPath().resolve(MISSING_ORPHANS_FILE).toFile();
        try (final PrintWriter writer = new PrintWriter(textFile)) {
            for (String cardName : missingCardOrphans) {
                writer.println(cardName);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return missingCardOrphans.size();
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
            if (cardData.getText() !=null) {
                writer.println("oracle="+cardData.getOracleText());
            } else {
                writer.println("oracle=NONE");
            }

        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
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
