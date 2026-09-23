package com.rynvortex.spddump.ui

/**
 * One card in the Operations grid. `badge` is the little pill in the top-right
 * (AVB 0, FRP, RECOVERY, FASTBOOT, POWER, CLI, DISABLED, FLASH, BACKUP, IMEI,
 * RESTORE, WIPE, SLOT A, SLOT B in the reference screenshots).
 *
 * `argsTemplate` are the literal spd_dump args to run, with placeholders like
 * {input} / {backup} / {xml} filled in from the Files tab or a picker before
 * running. These are TODO stand-ins - swap in the exact partition names /
 * command syntax your spd_dump build actually supports (see notes below each
 * entry in OperationCatalog).
 */
data class Operation(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val badgeIsWarning: Boolean = false,
    val requiresFile: FileRequirement = FileRequirement.NONE,
    val argsTemplate: List<String>,
    val confirmMessage: String? = null
)

enum class FileRequirement { NONE, SINGLE_IMAGE, XML }

/**
 * IMPORTANT: the verb names below (write_flash / read_flash / erase_flash / reset)
 * are placeholders for readability, NOT confirmed spd_dump syntax. The actual CLI's
 * verbs (from its own --help / README) look more like:
 *   fdl <file> <addr>, exec, r <partition> <addr> <size> <outfile>,
 *   r all <savepath>, w <partition> <file>, erase <partition>, reset,
 *   check_part <partition>, --kick / --kickto <mode>
 * and the exact set differs by which fork/build you're using (feature-phone BROM
 * vs smartphone FDL mode have different command subsets). Before wiring these into
 * FlashSession.runArgs, run the binary's own help/usage output and swap each
 * argsTemplate below for the real verbs + arg order it expects.
 */
object OperationCatalog {

    val all: List<Operation> = listOf(
        Operation(
            id = "unlock_bootloader",
            title = "Unlock Bootloader",
            subtitle = "This erases userdata and flips the unlock flag",
            badge = "DISABLED", // greyed out until a device is connected + confirmed
            argsTemplate = listOf("write_flash", "frp", "{zeros}"), // TODO: real unlock write target
            confirmMessage = "This wipes userdata and unlocks the bootloader. Continue?"
        ),
        Operation(
            id = "flash_partition",
            title = "Pasang Partisi", // "Flash Partition"
            subtitle = "Flash .img/.bin files from Input Files",
            badge = "FLASH",
            requiresFile = FileRequirement.SINGLE_IMAGE,
            argsTemplate = listOf("write_flash", "{partition}", "{input}")
        ),
        Operation(
            id = "backup_partition",
            title = "Cadangkan Partisi", // "Backup Partition"
            subtitle = "Backup boot, vendor_boot, dtbo, super, etc.",
            badge = "BACKUP",
            argsTemplate = listOf("read_flash", "{partition}", "0", "{size}", "{backup}")
        ),
        Operation(
            id = "backup_imei",
            title = "Cadangkan IMEI & NV",
            subtitle = "Backup prodnv, fixnv1, fixnv2",
            badge = "IMEI",
            argsTemplate = listOf("read_flash", "prodnv", "0", "{size}", "{backup}")
        ),
        Operation(
            id = "restore_backup",
            title = "Pulihkan Cadangan", // "Restore Backup"
            subtitle = "Restore write_partition from a prior backup",
            badge = "RESTORE",
            requiresFile = FileRequirement.SINGLE_IMAGE,
            argsTemplate = listOf("write_flash", "{partition}", "{backup}"),
            confirmMessage = "Overwrite this partition with the selected backup?"
        ),
        Operation(
            id = "wipe_userdata",
            title = "Wipe Userdata",
            subtitle = "Factory reset / wipe userdata partition",
            badge = "WIPE",
            badgeIsWarning = true,
            argsTemplate = listOf("erase_flash", "userdata"),
            confirmMessage = "This erases all user data on the device. Continue?"
        ),
        Operation(
            id = "slot_a",
            title = "Slot A (SPD)",
            subtitle = "Switch active boot slot to A",
            badge = "SLOT A",
            argsTemplate = listOf("write_flash", "misc", "{slot_a_bytes}") // TODO: correct misc offset/pattern
        ),
        Operation(
            id = "slot_b",
            title = "Slot B (SPD)",
            subtitle = "Switch active boot slot to B",
            badge = "SLOT B",
            argsTemplate = listOf("write_flash", "misc", "{slot_b_bytes}")
        ),
        Operation(
            id = "disable_verity",
            title = "Disable Verity",
            subtitle = "Disable AVB / dm-verity",
            badge = "AVB 0",
            argsTemplate = listOf("write_flash", "vbmeta", "{disabled_vbmeta}") // TODO: flag-cleared vbmeta blob
        ),
        Operation(
            id = "reset_frp",
            title = "Reset FRP Lock",
            subtitle = "Reset FRP / persist partition",
            badge = "FRP",
            argsTemplate = listOf("erase_flash", "frp"),
            confirmMessage = "Reset FRP lock on this device?"
        ),
        Operation(
            id = "reboot_recovery",
            title = "Reboot Recovery",
            subtitle = "Reboot device into recovery",
            badge = "RECOVERY",
            argsTemplate = listOf("reset", "recovery")
        ),
        Operation(
            id = "reboot_fastboot",
            title = "Reboot Fastboot",
            subtitle = "Reboot device into fastboot",
            badge = "FASTBOOT",
            argsTemplate = listOf("reset", "fastboot")
        ),
        Operation(
            id = "power_off",
            title = "Power Off Device",
            subtitle = "Shutdown device safely",
            badge = "POWER",
            argsTemplate = listOf("reset", "poweroff")
        ),
        Operation(
            id = "custom_cli",
            title = "Custom spd_dump",
            subtitle = "Type custom args...",
            badge = "CLI",
            argsTemplate = emptyList() // handled specially - opens a free-text dialog
        ),
        Operation(
            id = "repartition",
            title = "Repartition",
            subtitle = "Changes stock partition sizes (not dynamic)",
            badge = "XML",
            requiresFile = FileRequirement.XML,
            argsTemplate = listOf("repartition", "{xml}"), // TODO: only if your spd_dump build supports this verb
            confirmMessage = "Repartitioning can brick the device if the XML is wrong for this exact model. Continue?"
        )
    )
}
