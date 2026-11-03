package hr.smartbudget.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class TipTransakcije { PRIHOD, TROSAK }

/**
 * Iznosi se pohranjuju kao cijeli broj centi (Long), tako da je zaokruživanje na 2 decimale
 * točno i bez grešaka pomičnog zareza. Pretvorba u €: [hr.smartbudget.app.domain.Novac].
 */
@Entity(
    tableName = "transakcije",
    foreignKeys = [
        ForeignKey(
            entity = Kategorija::class,
            parentColumns = ["id"],
            childColumns = ["kategorijaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("kategorijaId"), Index("datumVrijeme")],
)
data class Transakcija(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tip: TipTransakcije,
    @ColumnInfo(name = "iznosCenti") val iznosCenti: Long,
    /** Epoch millis, lokalna vremenska zona uređaja. */
    val datumVrijeme: Long,
    val kategorijaId: Long,
    val opis: String = "",
    val nacinPlacanja: String = NacinPlacanja.KARTICA,
    /** Ako je unos nastao iz ponavljajućeg unosa: njegov id (bez stranog ključa, pravilo se smije obrisati). */
    val ponavljajuciId: Long? = null,
)

/**
 * Ponavljajući unos: isti prihod ili trošak svaki mjesec na zadani dan.
 * Ako mjesec nema taj dan (npr. 31.), unos se stvara zadnjeg dana mjeseca.
 */
@Entity(
    tableName = "ponavljajuci_unosi",
    foreignKeys = [
        ForeignKey(
            entity = Kategorija::class,
            parentColumns = ["id"],
            childColumns = ["kategorijaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("kategorijaId")],
)
data class PonavljajuciUnos(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tip: TipTransakcije,
    val iznosCenti: Long,
    val kategorijaId: Long,
    val opis: String = "",
    val nacinPlacanja: String = NacinPlacanja.KARTICA,
    /** 1–31 */
    val danUMjesecu: Int,
    /** Vrijeme unosa u minutama od ponoći. */
    val vrijemeMinuta: Int = 8 * 60,
    /** Datum prvog unosa (epoch dan); ranije se ništa ne stvara. */
    val pocetakEpochDan: Long,
    /** Datum zadnjeg stvorenog unosa (epoch dan), null ako još nijedan. */
    val zadnjiEpochDan: Long? = null,
    val aktivan: Boolean = true,
)

data class PonavljajuciSKategorijom(
    @Embedded val unos: PonavljajuciUnos,
    @Relation(parentColumn = "kategorijaId", entityColumn = "id")
    val kategorija: Kategorija,
)

object NacinPlacanja {
    const val GOTOVINA = "Gotovina"
    const val TEKUCI = "Tekući račun"
    const val KARTICA = "Kartica"
    val svi = listOf(KARTICA, TEKUCI, GOTOVINA)
}

@Entity(tableName = "kategorije")
data class Kategorija(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val naziv: String,
    val bojaHex: String,
    val ikonaNaziv: String,
    val tip: TipTransakcije,
    /** Opcionalni limit potrošnje po ciklusu, u centima. */
    val mjesecniLimitCenti: Long? = null,
)

/** Jedan redak (id = 1) s postavkama obračunskog ciklusa. */
@Entity(tableName = "postavke_ciklusa")
data class PostavkeCiklusa(
    @PrimaryKey val id: Int = 1,
    /** 1–31, npr. dan isplate plaće. */
    val pocetniDanUCiklusu: Int = 1,
    /**
     * true: ako mjesec nema odabrani dan, ciklus počinje zadnjeg dana tog mjeseca.
     * false: ciklus tada počinje prvog dana sljedećeg mjeseca.
     */
    val nacinObradeKrajaMjeseca: Boolean = true,
)

/**
 * Ručno zadani početak jednog ciklusa (umjesto zadanog dana u mjesecu).
 * [sidro] je mjesec ciklusa u obliku "2026-10"; kraj ciklusa je dan prije početka sljedećeg.
 */
@Entity(tableName = "prilagodbe_ciklusa")
data class PrilagodbaCiklusa(
    @PrimaryKey val sidro: String,
    val pocetakEpochDan: Long,
)

data class TransakcijaSKategorijom(
    @Embedded val transakcija: Transakcija,
    @Relation(parentColumn = "kategorijaId", entityColumn = "id")
    val kategorija: Kategorija,
)
