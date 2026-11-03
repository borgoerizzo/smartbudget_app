package hr.smartbudget.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Transakcija::class, Kategorija::class, PostavkeCiklusa::class, PonavljajuciUnos::class],
    version = 2,
    exportSchema = true,
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun transakcije(): TransakcijaDao
    abstract fun kategorije(): KategorijaDao
    abstract fun postavke(): PostavkeDao
    abstract fun ponavljajuci(): PonavljajuciDao

    companion object {
        @Volatile private var instance: BudgetDatabase? = null

        fun get(context: Context): BudgetDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BudgetDatabase::class.java, "smartbudget.db")
                .addCallback(Pocetni)
                .addMigrations(MIGRACIJA_1_2)
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
        }
    }
}
