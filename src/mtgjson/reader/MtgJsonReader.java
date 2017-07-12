package mtgjson.reader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Given a list of missing card names from Magarena this will attempt to match each
 * card name to an entry in the json feed from mgtjson.com and create a script file
 * for each matching card.
 */
public class MtgJsonReader {

    // All input data required should be stored in this folder (will fail if missing!).
    private static final String INPUT_FOLDER = "INPUT";

    // All data generated will be stored in this folder (it will be created if missing).
    private static final String OUTPUT_FOLDER = "OUTPUT";

    // This folder is automatically created in the OUTPUT_FOLDER.
    // Contains set of scripts files to be added to the Magarena "scripts_missing" folder.
    private static final String SCRIPTS_MISSING_FOLDER = "scripts_missing";

    // Optional. This folder is manually created in the INPUT_FOLDER.
    // This folder should contain scripts whose "image" property needs to be updated.
    private static final String INVALID_IMAGE_SCRIPTS_FOLDER = "invalid_image_scripts";

    // This file is created in the OUTPUT_FOLDER and lists any scripts in
    // INVALID_IMAGE_SCRIPTS_FOLDER which could not be updated.
    private static final String IMAGE_UPDATE_ERROR_LOG = "SkippedInvalidImageScripts.log";

    // Required. Place this file in the INPUT_FOLDER.
    // It is obtained from mtgjson.com. It contains every card grouped by set.
    private static final String JSON_FILE = "AllSets.json";

    // Required. Place this file in the INPUT_FOLDER.
    // This is a list of the cards which have not yet been implemented in Magarena.
    // In effect it is the list of cards in "AllCardNames.txt" minus those cards which
    // have a matching script file in the "scripts" folder. This file can be created
    // from the Cards Explorer screen by running Magarena in dev mode (-DdevMode=true).
    private static final String MISSING_CARDS_FILE = "CardsMissingInMagarena.txt";

    // This file is automatically created in the OUTPUT_FOLDER.
    // This file contains entries from MISSING_CARDS_FILE which have no matching entry
    // in JSON_FILE. Check the name for typos, strange characters, etc.
    private static final String MISSING_ORPHANS_FILE = "MissingCardOrphans.txt";

    // Optional. This file is manually created in the INPUT_FOLDER.
    // Use this file to override the automatically generated image link for a given script file.
    // This is applies to both the scripts generator and image line batch updater.
    private static final String PREDEFINED_IMAGES_FILE = "CardImages.txt";

    // This file is automatically created in the OUTPUT_FOLDER (for reference only).
    // list of all set codes from json feed sorted by release date in descending order.
    private static final String JSON_SETS_FILE = "JsonSetCodes.txt";

    // Set codes to be ignored in the json feed - no card data will be used from these sets.
    // Emphasis is on using base sets for extra language support.
    private static final Set<String> invalidSetCodes = new HashSet<>(
            Arrays.asList(
                //Not on MagicCards.info

                //No crops - Comment out the sets below if running for Orphaned files
                "C13", "M14", "C15", "THS", "BNG", "MM3", "AKH", "HOU"

            )
    );

    private static final Set<String> validSetCodes = new HashSet<>(
        Arrays.asList(
            "LEA", "LEB", "2ED", "ARN", "ATQ", "3ED", "LEG", "DRK", "FEM", "4ED", "ICE", "HML", "ALL", "MIR", "VIS",
            "5ED", "POR", "WTH", "TMP", "STH", "EXO", "PO2", "USG", "ULG", "6ED", "PTK", "UDS", "S99", "MMQ", "NMS",
            "PCY", "INV", "PLS", "7ED", "APC", "ODY", "TOR", "JUD", "ONS", "LGN", "SCG", "8ED", "MRD", "DST", "5DN",
            "CHK", "BOK", "SOK", "9ED", "RAV", "GPT", "DIS", "CSP", "CST", "TSB", "TSP", "PLC", "FUT", "10E", "LRW",
            "MOR", "SHM", "EVE", "ALA", "CON", "ARB", "M10", "ZEN", "WWK", "ROE", "M11", "SOM", "MBS", "NPH", "CMD",
            "M12", "ISD", "DKA", "AVR", "PC2", "M13", "RTR", "GTC", "DGM", "MMA", "M14", "THS", "C13", "BNG", "JOU",
            "CNS", "M15", "KTK", "C14", "FRF", "DTK", "MM2", "ORI", "BFZ", "C15", "OGW", "SOI", "EMA", "EMN", "CN2",
            "KLD", "C16", "AER", "MM3", "AKH", "HOU"

        )
    );

    private static final String ERRORS_FILE = "errors.txt";

    private static final List<String> setCodesList = new ArrayList<>();
    private static final HashMap<String, CardData> mtgcomCards = new HashMap<>();
    private static final List<String> magarenaMissingCards = new ArrayList<>();
    private static final HashMap<String, String> mtginfoSetsMap = new HashMap<>();
    private static final Map<String, String> cardImageLink = new TreeMap<>();
    private static final Map<String, String> predefinedCardImages = new HashMap<>();

    public static void main(String[] args) throws IOException {

        final long start_time = System.currentTimeMillis();
        System.out.println("\nRunning Magarena Scripts Generator...");

        if (!deleteOutputFolder()) {
            return;
        }

        loadJsonData();
        System.out.printf("-> Total unique cards identified in json feed = %d (see %s).\n",
                mtgcomCards.size(), getJsonFile());

        loadMissingMagarenaCards();
        System.out.printf("-> Total missing cards in Magarena = %d (see %s).\n",
                magarenaMissingCards.size(), getMissingCardsFile());

        loadPredefinedCardImages();

        // sort list of ALL card names from json file.
        final List<String> mtgcomCardNames = new ArrayList<>(mtgcomCards.keySet());
        Collections.sort(mtgcomCardNames);

        final int missingOrphans = saveListOfMissingCardOrphans(mtgcomCardNames);
        System.out.printf("-> Total missing cards which could not be matched in \"%s\" = %d (see %s).\n",
                JSON_FILE, missingOrphans, getMissingOrphansFile());

        // From this point, only interested in cards defined in MISSING_CARDS_FILE.
        mtgcomCardNames.retainAll(magarenaMissingCards);

        //saveReplacementOracle(mtgcomCardNames);

        saveMissingCardData(mtgcomCardNames);
        System.out.printf("-> Created %d script files in \"%s\".\n",
                mtgcomCardNames.size(), getScriptsMissingFolder()
        );

        updateScriptsImageProperty();

        logErrorDetails();

        final double duration = (double)(System.currentTimeMillis() - start_time) / 1000;
        System.out.printf("Finished in %.1f seconds.\n", duration);

    }

        private static boolean deleteOutputFolder() {
        boolean result = true;
        if (getOutputPath().toFile().exists()) {
            try {
                FileSysUtil.deleteDirectory(getOutputPath());
            } catch (IOException ex) {
                System.err.println(ex);
                result = false;
            }
        }
        return result;
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
                                new FileInputStream(getJsonFile()), "UTF-8"))) {

            final JsonParser parser = new JsonParser();
            final JsonElement element = parser.parse(reader);

            // list of set codes sorted by release date (map key) descending.
            final SortedMap<String, String> sortedSetCodes =
                    getSetCodesSortedByReleaseDateDesc(getSetCodes(), element);

            // save list of set codes for reference.
            logSetCodes(sortedSetCodes);
            loadMtgInfoSetsMap();

            for (Entry<String, String> entrySet : sortedSetCodes.entrySet()) {
                final String jsonSetCode = entrySet.getValue();
                if (isValidSetCode(jsonSetCode)) {
                    final JsonObject jsonSetObject = element.getAsJsonObject().get(jsonSetCode).getAsJsonObject();
                    final String setCode = getSetCode(jsonSetCode);
                    extractCardDataFromJson(jsonSetObject.getAsJsonArray("cards"), setCode);
                }
            }

        }

    }

    private static void extractCardDataFromJson(final JsonArray cards, final String setCode) {

        for (JsonElement jsonCardElement : cards) {

            JsonObject jsonCard = (JsonObject) jsonCardElement;
            String key = CardData.getId(jsonCard);

            if (!mtgcomCards.containsKey(key) && CardData.isValid(jsonCard)) {
                CardData card = new CardData(jsonCard, setCode);
                mtgcomCards.put(key, card);
                cardImageLink.put(card.getFilename(), card.getImageUrl());
            }
        }
    }


    private static String getSetCode(final String jsonSetCode) {
        String key = jsonSetCode.toUpperCase(Locale.ENGLISH).trim();
        return mtginfoSetsMap.containsKey(key) ? mtginfoSetsMap.get(key) : jsonSetCode;
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
        mtginfoSetsMap.put("CST", "cstd");
        // Starter Sets
        mtginfoSetsMap.put("POR", "po");
        mtginfoSetsMap.put("PTK", "p3k");
        mtginfoSetsMap.put("S99", "st");
        // Media Inserts
        mtginfoSetsMap.put("pMEI", "mbp");
}

    private static void logErrorDetails() {
        if (!CardData.cardImageErrors.isEmpty()) {
            final File textFile = getOutputPath().resolve(ERRORS_FILE).toFile();
            try (final PrintWriter writer = new PrintWriter(textFile)) {
                CardData.cardImageErrors.values().forEach(writer::println);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            System.out.printf("ERRORS = %d (see \\results\\%s)\n",
                    CardData.cardImageErrors.size(), ERRORS_FILE);
        }
    }

    private static void logSetCodes(final Map<String, String> sortedSetCodes) {
        final File textFile = getOutputPath().resolve(JSON_SETS_FILE).toFile();
        try (final PrintWriter writer = new PrintWriter(textFile)) {
            for (Entry<String, String> entrySet : sortedSetCodes.entrySet()) {
                final String key = entrySet.getKey();
                final String jsonSetCode = entrySet.getValue();
                final boolean isValidSetCode = isValidSetCode(jsonSetCode);
                final String setCode = getSetCode(jsonSetCode);
                if (isValidSetCode) {
                    if (setCode.equalsIgnoreCase(jsonSetCode)) {
                        writer.printf("%s\n", key);
                    } else {
                        writer.printf("%s -> %s\n", key, setCode);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
//        System.out.printf("-> Processing %d sets in release date reverse order (see \\results\\%s)\n",
//                sortedSetCodes.size(), JSON_SETS_FILE);
    }

    private static boolean isValidSetCode(final String setCode) {
        return validSetCodes.contains(setCode) && !invalidSetCodes.contains(setCode);

    }

    private static SortedMap<String, String> getSetCodesSortedByReleaseDateDesc(final String[] setCodes, final JsonElement element) {
        final SortedMap<String, String> sortedSetCodes = new TreeMap<>(Collections.reverseOrder());
        for (String setCode : setCodes) {
            final JsonObject setObject = element.getAsJsonObject().get(setCode).getAsJsonObject();
            final JsonElement releaseDate = setObject.get("releaseDate");
            if (releaseDate != null) {
                final String setReleaseDate = releaseDate.getAsString();
                final String key = setReleaseDate + " " + setCode;
                sortedSetCodes.put(key, setCode);
            }
        }
        return sortedSetCodes;
    }

    /**
     * Saves a list of the card names that are present in the missing cards list from
     * Magarena but which have no matching card name in the json file from mtgjson.com.
     */
    private static int saveListOfMissingCardOrphans(final Collection<String> mtgcomCardNames) {
        final List<String> missingCardOrphans = new ArrayList<>(magarenaMissingCards);
        missingCardOrphans.removeAll(mtgcomCardNames);
        Collections.sort(missingCardOrphans);
        final File textFile = getMissingOrphansFile();
        try (final PrintWriter writer = new PrintWriter(textFile)) {
            missingCardOrphans.forEach(writer::println);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return missingCardOrphans.size();
    }

    private static void saveMissingCardData(final Iterable<String> cardNames) {
        FileUtils.deleteQuietly(getScriptsMissingFolder().toFile());
        for (String cardName : cardNames) {
            saveCardData(mtgcomCards.get(cardName));
        }
    }

    private static void saveCardData(final CardData cardData) {

        // ensure unix style line endings.
        System.setProperty("line.separator", "\n");

        final String scriptFilename = cardData.getFilename();
        final Path filePath = getScriptsMissingFolder().resolve(scriptFilename);
        try (final PrintWriter writer = new PrintWriter(filePath.toString(), "UTF-8")) {
            writer.println("name=" + cardData.getCardName());
            writer.println("image=" + getCardImageUrl(scriptFilename, cardData.getImageUrl()));
            writer.println("value=2.500");
            writer.println("rarity=" + cardData.getRarity().replace("S", "R"));
            writer.println("type=" + cardData.getType());
            if (cardData.hasSubType()) {
                writer.println("subtype=" + cardData.getSubType());
            }
            if (cardData.hasColor()) {
                writer.println("color="+cardData.getColor());
            }
            if (cardData.hasManaCost()) {
                writer.println("cost=" + cardData.getManaCost());
            }
            if (cardData.hasPT()) {
                writer.println("pt=" + cardData.getPower() + "/" + cardData.getToughness());
            }
            if (cardData.hasLoyalty()) {
                writer.println("loyalty=" + cardData.getLoyalty());
            }
            if (cardData.hasAbilityText()) {
                writer.println("ability=" + cardData.getAbilityText());
            }
            if (cardData.hasEffectText()) {
                writer.println("effect=" + cardData.getEffectText());
            }
            writer.println("timing=" + cardData.getTiming());
            if (cardData.hasSubType()) {
                if (cardData.getSubType().contains("Aura")) {
                    writer.println("enchant=" + cardData.getEnchant());
                }
            }
            writer.println("oracle=" + (cardData.hasOracleText() ? cardData.getOracleText() : "NONE"));

        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /*private static void saveReplacementOracle(List<String> cardNames) {
        // ensure unix style line endings.
        System.setProperty("line.separator", "\n");

            // get individual cards
            for (String cardName : cardNames) {
                final CardData cardData = mtgcomCards.get(cardName);
                // create file stream
                if (cardData.hasOracleText() && cardData.getOracleText().contains("\\n")) {
                    final String scriptFilename = cardData.getFilename();
                    final Path filePath = getScriptsMissingFolder().resolve(scriptFilename);
                    try (final PrintWriter writer = new PrintWriter(filePath.toString(), "UTF-8")) {
                        writer.println("oracle="+cardData.getOracleText());
                    } catch (FileNotFoundException | UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
    }*/

    private static String getCardImageUrl(final String scriptFilename, final String defaultUrl) {
        return predefinedCardImages.containsKey(scriptFilename) ? predefinedCardImages.get(scriptFilename) : defaultUrl;
    }

    private static String[] getSetCodes() throws IOException {
        FileReader fileReader = new FileReader(getJsonFile());
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
            if (token == JsonToken.BEGIN_ARRAY) {
                handleArray(reader);
            } else if (token == JsonToken.END_ARRAY) {
                reader.endObject();
                return;
            } else {
                handleNonArrayToken(reader, token);
            }
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
            if (token == JsonToken.END_ARRAY) {
                reader.endArray();
                break;
            } else if (token == JsonToken.BEGIN_OBJECT) {
                handleObject(reader);
            } else {
                handleNonArrayToken(reader, token);
            }
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
        switch (token) {
            case NAME:
                setCodesList.add(reader.nextName());
                break;
            case STRING:
                System.out.println(reader.nextString());
                break;
            case NUMBER:
                System.out.println(reader.nextDouble());
                break;
            default:
                reader.skipValue();
                break;
        }
    }

    /**
     *  sorted list of missing card names from Magarena.
     */
    public static void loadMissingMagarenaCards() {
        magarenaMissingCards.clear();
        try {
            final Path filePath = getMissingCardsFile().toPath();
            magarenaMissingCards.addAll(Files.readAllLines(filePath, Charset.defaultCharset()).stream().map(String::trim).collect(Collectors.toList()));
        } catch (final IOException ex) {
           throw new RuntimeException(ex);
        }
    }

    private static Path getInputPath() {
        return getFolderPath(Paths.get(INPUT_FOLDER));
    }

    private static Path getOutputPath() {
        return getFolderPath(Paths.get(OUTPUT_FOLDER));
    }

    private static Path getScriptsMissingFolder() {
        return getFolderPath(getOutputPath().resolve(SCRIPTS_MISSING_FOLDER));
    }

    private static File getJsonFile() {
        return getInputPath().resolve(JSON_FILE).toFile();
    }

    private static File getMissingCardsFile() {
        return getInputPath().resolve(MISSING_CARDS_FILE).toFile();
    }

    private static File getMissingOrphansFile() {
        return getOutputPath().resolve(MISSING_ORPHANS_FILE).toFile();
    }

    private static File getPredefinedImagesFile() {
        return getInputPath().resolve(PREDEFINED_IMAGES_FILE).toFile();
    }

    private static Path getFolderPath(final Path folderPath) {
        if (!Files.isDirectory(folderPath)) {
            try {
                Files.createDirectory(folderPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return folderPath;
    }

    private static void updateScriptsImageProperty() {

        final Path inputFolder = getFolderPath(getInputPath().resolve(INVALID_IMAGE_SCRIPTS_FOLDER));
        final File[] scriptFiles = getSortedInvalidImageScriptFiles(inputFolder.toFile());
        final int totalScripts = scriptFiles.length;

        if (totalScripts > 0) {
            final Path outputFolder = getFolderPath(getOutputPath().resolve(INVALID_IMAGE_SCRIPTS_FOLDER));

            System.out.println("Running batch image link updater...");
            System.out.printf("-> Updating %d script files in \"%s\"...\n",
                    totalScripts, inputFolder
            );

            final List<String> skippedFiles = new ArrayList<>();
            int updateCount = 0;
            for (File scriptFile : scriptFiles) {
                try {
                    final String scriptFilename = scriptFile.getName();
                    if (cardImageLink.containsKey(scriptFilename)) {
                        final String imageUrl = cardImageLink.get(scriptFilename);
                        replaceScriptImageLink(scriptFile, outputFolder, imageUrl);
                        updateCount++;
                    } else {
                        skippedFiles.add(scriptFilename);
                    }
                } catch (InvalidPathException ex) {
                    System.err.println(ex);
                } catch (IOException | RuntimeException ex) {
                    System.err.println(ex);
                }
            }

            System.out.printf("-> Updated image property in %d script files in \"%s\".\n",
                    updateCount, outputFolder
            );

            if (!skippedFiles.isEmpty()) {
                saveSkippedFilesLog(skippedFiles);
            }

        }
    }

    private static File[] getSortedInvalidImageScriptFiles(final File scriptsFolder) {
        final File[] files = scriptsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".txt"));
        Arrays.sort(files);
        return files;
    }

    private static void saveSkippedFilesLog(List<String> skippedFiles) {
        final File textFile = getOutputPath().resolve(IMAGE_UPDATE_ERROR_LOG).toFile();
        try (final PrintWriter writer = new PrintWriter(textFile)) {
            skippedFiles.forEach(writer::println);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.printf("-> Failed to update image property in %d script files. (see %s).\n",
                skippedFiles.size(),
                textFile
        );
    }

    private static void replaceScriptImageLink(final File inputScript, final Path outputFolder, final String imageUrl) throws IOException {
        final File outputScript = outputFolder.resolve(inputScript.getName()).toFile();
        try (
                final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputScript), "UTF-8"));
                final PrintWriter bw = new PrintWriter(outputScript, "UTF-8")) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("image=")) {
                            bw.println("image=" + getCardImageUrl(inputScript.getName(), imageUrl));
                        } else {
                            bw.println(line);
                        }
                    }
                }
    }

    private static void loadPredefinedCardImages() {
        if (getPredefinedImagesFile().exists()) {
            final Properties prop = new Properties();
            try (final InputStream in = new FileInputStream(getPredefinedImagesFile())) {
                prop.load(in);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            for (final String scriptName : prop.stringPropertyNames()) {
                predefinedCardImages.put(scriptName, prop.getProperty(scriptName));
            }
        }
    }

}
