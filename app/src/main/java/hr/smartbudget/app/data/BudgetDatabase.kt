package hr.smartbudget.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Transakcija::class, Kategorija::class, PostavkeCiklusa::class, PonavljajuciUnos::class,
        PrilagodbaCiklusa::class, Racun::class, CiljStednje::class, UplataCilja::class, Novcanik::class, PromjenaNovcanika::class,
        Pozicija::class, PromjenaPozicije::class, Rezervacija::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun transakcije(): TransakcijaDao
    abstract fun kategorije(): KategorijaDao
    abstract fun postavke(): PostavkeDao
    abstract fun ponavljajuci(): PonavljajuciDao
    abstract fun prilagodbe(): PrilagodbaDao
    abstract fun racuni(): RacunDao
    abstract fun ciljevi(): CiljDao
    abstract fun uplate(): UplataDao
    abstract fun novcanici(): NovcanikDao
    abstract fun pozicije(): PozicijaDao
    abstract fun rezervacije(): RezervacijaDao

    companion object {
        /** Tekući račun, Kartica i Gotovina (dosadašnji načini plaćanja). */
        internal fun pocetniRacuni(db: SupportSQLiteDatabase) {
            listOf(
                Triple("Tekući račun", "#4D8BFF", "bank"),
                Triple("Kartica", "#00D8F6", "card"),
                Triple("Gotovina", "#FFC247", "wallet"),
            ).forEachIndexed { i, (naziv, boja, ikona) ->
                db.execSQL(
                    "INSERT INTO racuni (naziv, bojaHex, ikonaNaziv, pocetnoStanjeCenti, redoslijed) VALUES (?, ?, ?, 0, ?)",
                    arrayOf<Any>(naziv, boja, ikona, i),
                )
            }
        }

        @Volatile private var instance: BudgetDatabase? = null

        fun get(context: Context): BudgetDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BudgetDatabase::class.java, "smartbudget.db")
                .addCallback(Pocetni)
                .addMigrations(MIGRACIJA_1_2, MIGRACIJA_2_3, MIGRACIJA_3_4, MIGRACIJA_4_5, MIGRACIJA_5_6, MIGRACIJA_6_7, MIGRACIJA_7_8, MIGRACIJA_8_9, MIGRACIJA_9_10, MIGRACIJA_10_11, MIGRACIJA_11_12, MIGRACIJA_12_13)
                .build()
                .also { instance = it }
        }
    }

    /** v2: ponavljajući unosi i veza transakcije na pravilo iz kojeg je nastala. */
    private object MIGRACIJA_1_2 : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ponavljajuci_unosi` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tip` TEXT NOT NULL, `iznosCenti` INTEGER NOT NULL, `kategorijaId` INTEGER NOT NULL, `opis` TEXT NOT NULL, " +
                    "`nacinPlacanja` TEXT NOT NULL, `danUMjesecu` INTEGER NOT NULL, `vrijemeMinuta` INTEGER NOT NULL, " +
                    "`pocetakEpochDan` INTEGER NOT NULL, `zadnjiEpochDan` INTEGER, `aktivan` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`kategorijaId`) REFERENCES `kategorije`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ponavljajuci_unosi_kategorijaId` ON `ponavljajuci_unosi` (`kategorijaId`)")
            db.execSQL("ALTER TABLE `transakcije` ADD COLUMN `ponavljajuciId` INTEGER")
        }
    }

    /** v3: ručno zadani rasponi pojedinih ciklusa. */
    private object MIGRACIJA_2_3 : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `prilagodbe_ciklusa` (`sidro` TEXT NOT NULL, `pocetakEpochDan` INTEGER NOT NULL, PRIMARY KEY(`sidro`))",
            )
        }
    }

    /**
     * v4: računi (postojeći načini plaćanja postaju računi), ciljevi štednje,
     * prijenosi, podijeljeni troškovi i fotografije računa.
     */
    private object MIGRACIJA_3_4 : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `racuni` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `naziv` TEXT NOT NULL, " +
                    "`bojaHex` TEXT NOT NULL, `ikonaNaziv` TEXT NOT NULL, `pocetnoStanjeCenti` INTEGER NOT NULL, `redoslijed` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ciljevi` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `naziv` TEXT NOT NULL, " +
                    "`ciljCenti` INTEGER NOT NULL, `ustedjenoCenti` INTEGER NOT NULL, `rokEpochDan` INTEGER, `bojaHex` TEXT NOT NULL, `ikonaNaziv` TEXT NOT NULL)",
            )
            db.execSQL("ALTER TABLE `transakcije` ADD COLUMN `jePrijenos` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `transakcije` ADD COLUMN `grupaId` INTEGER")
            db.execSQL("ALTER TABLE `transakcije` ADD COLUMN `slika` TEXT")
            pocetniRacuni(db)
            // Načini plaćanja koji već postoje u transakcijama, a nisu među početnim računima.
            db.execSQL(
                "INSERT INTO racuni (naziv, bojaHex, ikonaNaziv, pocetnoStanjeCenti, redoslijed) " +
                    "SELECT DISTINCT nacinPlacanja, '#9D4EDD', 'wallet', 0, 10 FROM transakcije " +
                    "WHERE nacinPlacanja NOT IN (SELECT naziv FROM racuni)",
            )
        }
    }

    /** v5: povijest uplata na ciljeve i datum postavljanja cilja (tempo štednje). */
    private object MIGRACIJA_4_5 : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ciljevi` ADD COLUMN `kreiranEpochDan` INTEGER")
            db.execSQL("UPDATE `ciljevi` SET `kreiranEpochDan` = ?", arrayOf<Any>(java.time.LocalDate.now().toEpochDay()))
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `uplate_cilja` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ciljId` INTEGER NOT NULL, " +
                    "`iznosCenti` INTEGER NOT NULL, `epochDan` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_uplate_cilja_ciljId` ON `uplate_cilja` (`ciljId`)")
            // Već ušteđeno postaje prva uplata, da povijest odgovara stanju.
            db.execSQL("INSERT INTO `uplate_cilja` (ciljId, iznosCenti, epochDan) SELECT id, ustedjenoCenti, kreiranEpochDan FROM `ciljevi` WHERE ustedjenoCenti != 0")
        }
    }

    /** v6: novčanici štednje u više valuta i kriptovalutama. */
    private object MIGRACIJA_5_6 : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `novcanici` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `naziv` TEXT NOT NULL, " +
                    "`valuta` TEXT NOT NULL, `redoslijed` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `promjene_novcanika` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `novcanikId` INTEGER NOT NULL, " +
                    "`jedinice` INTEGER NOT NULL, `epochDan` INTEGER NOT NULL, `ulozenoCenti` INTEGER, `napomena` TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_promjene_novcanika_novcanikId` ON `promjene_novcanika` (`novcanikId`)")
        }
    }

    /** v7: uplate na ciljeve u stranim valutama i kriptovalutama. */
    private object MIGRACIJA_6_7 : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `uplate_cilja` ADD COLUMN `valuta` TEXT")
        }
    }

    /**
     * v8: oznaka prijenosa na štednju i sa nje. Dosadašnji prijenosi na štednju imaju samo jedan dio
     * (odljev s računa), za razliku od prijenosa između računa koji imaju dva.
     */
    private object MIGRACIJA_7_8 : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transakcije` ADD COLUMN `naStednju` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE transakcije SET naStednju = 1 WHERE jePrijenos = 1 AND grupaId IS NOT NULL AND grupaId IN " +
                    "(SELECT grupaId FROM transakcije WHERE jePrijenos = 1 AND grupaId IS NOT NULL GROUP BY grupaId HAVING COUNT(*) = 1)",
            )
        }
    }

    /**
     * v9: veza uplate na cilj i promjene novčanika na prijenos koji ih je stvorio, da brisanje
     * prijenosa poništi i štednju. Stariji prijenosi se povezuju pri brisanju po danu i iznosu.
     */
    private object MIGRACIJA_8_9 : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `uplate_cilja` ADD COLUMN `grupaId` INTEGER")
            db.execSQL("ALTER TABLE `promjene_novcanika` ADD COLUMN `grupaId` INTEGER")
        }
    }

    /** v10: ulaganja u vlasništvu (dionice, ETF-ovi, obveznice, roba) i njihove kupnje i prodaje. */
    private object MIGRACIJA_9_10 : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pozicije` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `simbol` TEXT NOT NULL, " +
                    "`naziv` TEXT NOT NULL, `vrsta` TEXT NOT NULL, `valuta` TEXT NOT NULL, `redoslijed` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `promjene_pozicija` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pozicijaId` INTEGER NOT NULL, " +
                    "`kolicinaMikro` INTEGER NOT NULL, `epochDan` INTEGER NOT NULL, `ulozenoCenti` INTEGER, `napomena` TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_promjene_pozicija_pozicijaId` ON `promjene_pozicija` (`pozicijaId`)")
        }
    }

    /**
     * v11: ručni unos državnih zapisa i obveznica bez tržišne cijene (iznos pri dospijeću) te rok
     * za sva ulaganja i novčanike (podsjetnici prije isteka).
     */
    private object MIGRACIJA_10_11 : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `pozicije` ADD COLUMN `nominalCenti` INTEGER")
            db.execSQL("ALTER TABLE `pozicije` ADD COLUMN `dospijeceEpochDan` INTEGER")
            db.execSQL("ALTER TABLE `novcanici` ADD COLUMN `rokEpochDan` INTEGER")
        }
    }

    /** v12: kamo i kada ide dobit ručnog zapisa (odmah ili pri dospijeću) i veza na upisanu dobit. */
    private object MIGRACIJA_11_12 : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `pozicije` ADD COLUMN `dobitNa` TEXT")
            db.execSQL("ALTER TABLE `pozicije` ADD COLUMN `dobitKada` TEXT")
            db.execSQL("ALTER TABLE `pozicije` ADD COLUMN `dobitGrupaId` INTEGER")
            db.execSQL("ALTER TABLE `pozicije` ADD COLUMN `dobitKnjizena` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v13: tekući račun s ručno unesenim stanjem i rezervirana sredstva. */
    private object MIGRACIJA_12_13 : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `racuni` ADD COLUMN `tekuci` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `racuni` ADD COLUMN `sidroCenti` INTEGER")
            db.execSQL("ALTER TABLE `racuni` ADD COLUMN `sidroMillis` INTEGER")
            oznaciTekuci(db)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `rezervacije` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `iznosCenti` INTEGER NOT NULL, " +
                    "`napomena` TEXT NOT NULL, `kreiranMillis` INTEGER NOT NULL, `oslobodenoMillis` INTEGER)",
            )
        }
    }

    /** Početne kategorije i postavke pri prvom pokretanju; korisnik ih poslije slobodno mijenja. */
    private object Pocetni : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val pocetne = listOf(
                Triple("Namirnice", "#FFC247", "cart") to "TROSAK",
                Triple("Dom i kućanstvo", "#4D8BFF", "home") to "TROSAK",
                Triple("Režije", "#9D4EDD", "bolt") to "TROSAK",
                Triple("Prijevoz", "#FF8A3D", "car") to "TROSAK",
                Triple("Restorani i kava", "#F15BB5", "coffee") to "TROSAK",
                Triple("Zabava", "#00D8F6", "star") to "TROSAK",
                Triple("Pretplate", "#BEF264", "repeat") to "TROSAK",
                Triple("Zdravlje", "#2DD4BF", "heart") to "TROSAK",
                Triple("Plaća", "#00F5A0", "brief") to "PRIHOD",
                Triple("Ostali prihodi", "#00D8F6", "wallet") to "PRIHOD",
            )
            pocetne.forEach { (k, tip) ->
                db.execSQL(
                    "INSERT INTO kategorije (naziv, bojaHex, ikonaNaziv, tip, mjesecniLimitCenti) VALUES (?, ?, ?, ?, NULL)",
                    arrayOf(k.first, k.second, k.third, tip),
                )
            }
            db.execSQL("INSERT INTO postavke_ciklusa (id, pocetniDanUCiklusu, nacinObradeKrajaMjeseca) VALUES (1, 1, 1)")
            pocetniRacuni(db)
            oznaciTekuci(db)
        }
    }
}

/** Račun "Tekući račun" (ili prvi račun) postaje tekući, ako nijedan još nije. */
internal fun oznaciTekuci(db: SupportSQLiteDatabase) {
    db.execSQL(
        "UPDATE racuni SET tekuci = 1 WHERE (SELECT COUNT(*) FROM racuni WHERE tekuci = 1) = 0 AND id = " +
            "COALESCE((SELECT id FROM racuni WHERE naziv = 'Tekući račun' ORDER BY id LIMIT 1), (SELECT id FROM racuni ORDER BY redoslijed, id LIMIT 1))",
    )
}
