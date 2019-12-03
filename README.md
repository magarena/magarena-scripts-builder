# Magarena Scripts Generator

Given a list of missing card names from Magarena this will attempt to match each
card name to an entry in the json feed from mgtjson.com and create a script file
for each matching card.

## Batch Image Link Updater
This can also be used to update the ``image`` property for multiple script files - see ``INVALID_IMAGE_SCRIPTS_FOLDER`` below.

## Instructions
Please make sure the required files are present in the ``INPUT`` folder (see below), then run ``MtgJsonReader`` from your preferred IDE.

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
    private static final String JSON_FILE = "AllPrintings.json";

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
