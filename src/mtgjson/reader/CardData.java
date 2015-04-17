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
    private String subType      = null;
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

        if (jsonCard.has("manaCost"))   { setManaCost(jsonCard.get("manaCost").getAsString()); }

        if (jsonCard.has("colors") && jsonCard.has("manaCost")==false) {
            final String colors = jsonCard.get("colors")
                    .toString()
                    .replace("Blue", "U")
                    .replaceAll("\\W", "")
                    .replaceAll("[a-z]", "")
                    .toLowerCase();
            setColor(colors);
        }

        if (jsonCard.has("power"))      { setPower(jsonCard.get("power").getAsString()); }
        if (jsonCard.has("toughness"))  { setToughness(jsonCard.get("toughness").getAsString()); }
        if (jsonCard.has("text"))       { setText(jsonCard.get("text").getAsString()); }

        if (jsonCard.has("supertypes")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = jsonCard.getAsJsonArray("supertypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            final String superTypes =sb.toString().substring(0,sb.toString().length()-1);
            setSuperType(superTypes);
        }

        if (jsonCard.has("types")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = jsonCard.getAsJsonArray("types");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            final String typeValues = sb.toString().substring(0, sb.toString().length() - 1);
            setType(typeValues);
        }

        if (jsonCard.has("subtypes")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = jsonCard.getAsJsonArray("subtypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                final String subType = cardTypes.get(j).toString();
                sb.append(subType
                            .replace("\"", "")
                            .replace(" ", "_")
                            .replace("’s", "'s")
                            .replace("-", "_")).append(",");
            }
            final String typeValues = sb.toString().substring(0, sb.toString().length() - 1);
            setSubType(typeValues);
        }

        if (jsonCard.has("text")) {
            if (jsonCard.has("types") && (type.contains("Instant") || type.contains("Sorcery"))) {
                final String effect = text
                                        .replaceAll("^\\(.+?\\)\n","")
                                        .replaceAll("^.+— ", "")
                                        .replace("\n", "~")
                                        .replaceAll("~.+? — ","~")
                                        .replaceAll(" \\(.+?\\)", "")
                                        .replaceAll("\n~•", "~•")
                                        .replaceFirst("~•", " (1)")
                                        .replaceFirst("~•", " (2)")
                                        .replaceFirst("~•", " (3)")
                                        .replaceFirst("~•", " (4)")
                                        .replace(getCardName(false), "SN");
                setEffectText(effect);
            } else {
                final String ability = text
                                        .replaceAll("^\\(.+?\\)\n","")
                                        .replaceAll("^.+— ", "")
                                        .replace("\n", ";\\\n        ")
                                        .replace(";\\\n        •", " \\\n        •")
                                        .replaceAll(" \\(.+?\\)", "")
                                        .replaceFirst(" •", " (1)")
                                        .replaceFirst(" •", " (2)")
                                        .replaceFirst(" •", " (3)")
                                        .replaceFirst(" •", " (4)")
                                        .replace(getCardName(false), "SN");
                setAbilityText(ability);
            }
            final String oracle = text
                                    .replaceAll("^\\(.+?\\)\n", "")
                                    .replace(".\n", ". ")
                                    .replace("\n", ". ")
                                    .replaceAll(" \\(.+?\\)", "")
                                    .replace("..", ".")
                                    .replace("—.", "—")
                                    .replace(". .", ".");
            setOracleText(oracle);
        }
    }

    public String getCardName(final boolean replaceNonAscii) {
        if (replaceNonAscii) {
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
        } else {
            return cardName;
        }
    }
    
    private void extractCardName(final JsonObject json) {
        this.cardName = getRawCardName(json);
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
    public void setManaCost(String manaCost) {
        this.manaCost = manaCost;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getType() {
        if (getSuperType() != null) {
            return getSuperType() + "," + type;
        } else {
            return type;
        }
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getFilename() {
        return getCardName(true).replaceAll("[^A-Za-z0-9]", "_") + ".txt";
    }

    public String getPower() {
        return power;
    }
    public void setPower(String power) {
        this.power = power;
    }

    public String getToughness() {
        return toughness;
    }
    public void setToughness(String toughness) {
        this.toughness = toughness;
    }

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    public String getSuperType() {
        return superType;
    }

    public void setSuperType(String superType) {
        this.superType = superType;
    }

    public String getSubType() {
        return subType;
    }
    public void setSubType(String subType) {
        this.subType = subType;
    }

    public String getEffectText() {
        return effectText;
    }
    public void setEffectText(String effect) {
        this.effectText = effect;
    }

    public String getAbilityText() {
        return abilityText;
    }
    public void setAbilityText(String ability) {
        this.abilityText = ability;
    }

    public String getOracleText() {
        return oracleText;
    }

    public void setOracleText(String oracleText) {
        this.oracleText = oracleText;
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
        final String key = card.getCardName(false);        
        if (!cardImageErrors.containsKey(key)) {
            cardImageErrors.put(key, errorDetails);
        }
    }

    private static void clearCardImageError(final CardData card) {
        final String key = card.getCardName(false);
        if (cardImageErrors.containsKey(key)) {
            cardImageErrors.remove(key);
        }
    }

    public static String getRawCardName(final JsonObject card) {
        return card.get("name").getAsString().trim();
    }

    public static String getRarity(final JsonObject card) {
        return card.get("rarity").getAsString().substring(0, 1);
    }
    
    public static boolean isValid(final JsonObject jsonCard) {
        return !CardData.getRarity(jsonCard).contentEquals("S") &&
               (jsonCard.has("number") || jsonCard.has("multiverseid"));
    }

}
