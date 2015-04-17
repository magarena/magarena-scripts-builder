package mtgjson.reader;

import java.io.UnsupportedEncodingException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.SortedMap;
import java.util.TreeMap;

class CardData {

    public static final SortedMap<String, String> cardImageErrors = new TreeMap<>();

    private String cardName;
    private String imageUrl = "";
    private String rarity;
    private String manaCost;
    private String type;
    private String color        = null;
    private String superType    = null;
    private String subTypes     = null;
    private String power        = null;
    private String toughness    = null;
    private String text         = null;
    private String effectText   = null;
    private String abilityText  = null;
    private String oracleText   = null;
    private final String setCode;

    public CardData(final JsonObject jsonCard, final String setCode) throws UnsupportedEncodingException {

        this.setCode = setCode;

        extractCardName(jsonCard);
        extractImageUrl(jsonCard);
        extractRarity(jsonCard);
        extractManaCost(jsonCard);
        extractColor(jsonCard);
        extractPower(jsonCard);
        extractToughness(jsonCard);
        extractSuperTypes(jsonCard);
        extractTypes(jsonCard);
        extractSubTypes(jsonCard);

        if (jsonCard.has("text")) {
            setText(jsonCard.get("text").getAsString());
            if (jsonCard.has("types") && (type.contains("Instant") || type.contains("Sorcery"))) {
                extractEffectText(jsonCard);
            } else {
                extractAbilityText(jsonCard);
            }
            extractOracleText(jsonCard);
        }
    }

    public String getCardName() {
        return cardName;
    }

    private String getCardNameAsAscii() {
        return cardName
                .replace("Æ", "_")
                .replace("á", "_")
                .replace("à", "_")
                .replace("â", "_")
                .replace("é", "_")
                .replace("í", "_")
                .replace("ö", "_")
                .replace("ú", "_")
                .replace("û", "_");
    }
    
    private void extractCardName(final JsonObject json) {
        this.cardName = json.get("name").getAsString();
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getRarity() {
        return rarity;
    }

    private void extractRarity(final JsonObject json) {
        this.rarity = getRarity(json);
    }

    public String getManaCost() {
        return manaCost;
    }

    private void extractManaCost(final JsonObject json) {
        if (json.has("manaCost")) {
            this.manaCost = json.get("manaCost").getAsString();
        }
    }

    public String getColor() {
        return color;
    }

    private void extractColor(final JsonObject json) {
        if (json.has("colors") && json.has("manaCost") == false) {
            this.color = json.get("colors")
                    .toString()
                    .replace("Blue", "U")
                    .replaceAll("\\W", "")
                    .replaceAll("[a-z]", "")
                    .toLowerCase();
        }
    }

    public String getType() {
        if (superType != null) {
            return superType + "," + type;
        } else {
            return type;
        }
    }
    
    private void extractTypes(final JsonObject json) {
        if (json.has("types")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = json.getAsJsonArray("types");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            final String typeValues = sb.toString().substring(0, sb.toString().length() - 1);
            this.type = typeValues;
        }
    }

    public String getFilename() {
        return getCardNameAsAscii().replaceAll("[^A-Za-z0-9]", "_") + ".txt";
    }

    public String getPower() {
        return power;
    }

    private void extractPower(final JsonObject json) {
        if (json.has("power")) {
            this.power = json.get("power").getAsString();
        }
    }

    public String getToughness() {
        return toughness;
    }
    
    private void extractToughness(final JsonObject json) {
        if (json.has("toughness")) {
            this.toughness = json.get("toughness").getAsString();
        }
    }

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    private void extractSuperTypes(final JsonObject json) {
        if (json.has("supertypes")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = json.getAsJsonArray("supertypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            final String superTypes =sb.toString().substring(0,sb.toString().length()-1);
            this.superType = superTypes;
        }
    }

    public String getSubType() {
        return subTypes;
    }
    
    private void extractSubTypes(final JsonObject json) {
        if (json.has("subtypes")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = json.getAsJsonArray("subtypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                final String subType = cardTypes.get(j).toString();
                sb.append(subType
                        .replace("\"", "")
                        .replace(" ", "_")
                        .replace("’s", "'s")
                        .replace("-", "_")).append(",");
            }
            final String typeValues = sb.toString().substring(0, sb.toString().length() - 1);
            this.subTypes = typeValues;
        }
    }

    public String getEffectText() {
        return effectText;
    }
    
    private void extractEffectText(final JsonObject json) {
        this.effectText = json.get("text").getAsString()
                .replaceAll("^\\(.+?\\)\n", "")
                .replaceAll("^.+— ", "")
                .replace("\n", "~")
                .replaceAll("~.+? — ", "~")
                .replaceAll(" \\(.+?\\)", "")
                .replaceAll("\n~•", "~•")
                .replaceFirst("~•", " (1)")
                .replaceFirst("~•", " (2)")
                .replaceFirst("~•", " (3)")
                .replaceFirst("~•", " (4)")
                .replace(getCardName(), "SN");
    }

    public String getAbilityText() {
        return abilityText;
    }
        
    private void extractAbilityText(final JsonObject json) {
        this.abilityText = json.get("text").getAsString()
                .replaceAll("^\\(.+?\\)\n", "")
                .replaceAll("^.+— ", "")
                .replace("\n", ";\\\n        ")
                .replace(";\\\n        •", " \\\n        •")
                .replaceAll(" \\(.+?\\)", "")
                .replaceFirst(" •", " (1)")
                .replaceFirst(" •", " (2)")
                .replaceFirst(" •", " (3)")
                .replaceFirst(" •", " (4)")
                .replace(getCardName(), "SN");
    }

    public String getOracleText() {
        return oracleText;
    }

    private void extractOracleText(final JsonObject json) {
        if (json.has("text")) {
            this.oracleText = json.get("text").getAsString()
                    .replaceAll("^\\(.+?\\)\n", "")
                    .replace(".\n", ". ")
                    .replace("\n", ". ")
                    .replaceAll(" \\(.+?\\)", "")
                    .replace("..", ".")
                    .replace("—.", "—")
                    .replace(". .", ".");
        }
    }

    public String getTiming() {
        if (type.contains("Instant")) {
            return "removal";
        } else {
            return "main";
        }
    }

    private void extractImageUrl(final JsonObject json) throws UnsupportedEncodingException {

        if (json.has("number")) {
            this.imageUrl = String.format(
                    "http://magiccards.info/scans/en/%s/%s.jpg",
                    setCode.toLowerCase(),
                    json.get("number").getAsString()
            );
            clearCardImageError(this);

        } else if (json.has("multiverseid")) {
            this.imageUrl = setCode.toLowerCase();
            clearCardImageError(this);

        } else {
            addCardImageError(this, String.format("%s has no number or multiverseid - cannot set {image} property.", cardName));
        }
        
    }
    
    private static void addCardImageError(final CardData card, final String errorDetails) {
        final String key = card.getCardName();        
        if (!cardImageErrors.containsKey(key)) {
            cardImageErrors.put(key, errorDetails);
        }
    }

    private static void clearCardImageError(final CardData card) {
        final String key = card.getCardName();
        if (cardImageErrors.containsKey(key)) {
            cardImageErrors.remove(key);
        }
    }

    public static String getId(final JsonObject card) {
        return card.get("name").getAsString().trim();
    }

    public static String getRarity(final JsonObject card) {
        return card.get("rarity").getAsString().substring(0, 1);
    }
    
    public static boolean isValid(final JsonObject jsonCard) {
        return !CardData.getRarity(jsonCard).contentEquals("S") &&
               (jsonCard.has("number") || jsonCard.has("multiverseid"));
    }

    public boolean hasSubType() {
        return getSubType() != null;
    }

    public boolean hasColor() {
        return !hasManaCost() && getColor() != null;
    }

    public boolean hasManaCost() {
        return getManaCost() != null;
    }

    public boolean hasPT() {
        return getPower() != null && getToughness() != null;
    }

    public boolean hasEffectText() {
        return getText() != null && getEffectText() != null;
    }

    public boolean hasAbilityText() {
        return getText() != null && !hasEffectText() && getAbilityText() != null;
    }

    public boolean hasOracleText() {
        return getText() != null;
    }

}
