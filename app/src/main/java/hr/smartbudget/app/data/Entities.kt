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
    /** Dio prijenosa između računa: ne broji se u prihode/troškove, samo mijenja stanja računa. */
    @ColumnInfo(defaultValue = "0") val jePrijenos: Boolean = false,
    /** Zajednički id za povezane unose: dva dijela prijenosa ili dijelovi podijeljenog troška. */
    val grupaId: Long? = null,
    /** Putanja fotografije računa u internoj memoriji aplikacije. */
    val slika: String? = null,
    /**
     * Prijenos s računa na štednju (trošak) ili sa štednje natrag na račun (prihod).
     * Nije ni prihod ni trošak, ali umanjuje (odnosno vraća) stanje ciklusa.
     */
    @ColumnInfo(defaultValue = "0") val naStednju: Boolean = false,
)

/**
 * Račun (tekući, kartica, gotovina, štednja...). Transakcija pripada računu po nazivu
 * ([Transakcija.nacinPlacanja]); stanje računa = početno stanje + njegove transakcije.
 */
@Entity(tableName = "racuni")
data class Racun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val naziv: String,
    val bojaHex: String,
    val ikonaNaziv: String,
    val pocetnoStanjeCenti: Long = 0,
    val redoslijed: Int = 0,
)

/** Cilj štednje s rokom; ušteđeni iznos korisnik ručno povećava uplatama. */
@Entity(tableName = "ciljevi")
data class CiljStednje(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val naziv: String,
    val ciljCenti: Long,
    val ustedjenoCenti: Long = 0,
    val rokEpochDan: Long? = null,
    val bojaHex: String,
    val ikonaNaziv: String,
    /** Dan kad je cilj postavljen (za tempo štednje); null kod starih kopija. */
    val kreiranEpochDan: Long? = null,
)

/** Jedna uplata na cilj štednje (negativna = povlačenje), za povijest i tempo. */
@Entity(tableName = "uplate_cilja", indices = [Index("ciljId")])
data class UplataCilja(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ciljId: Long,
    val iznosCenti: Long,
    val epochDan: Long,
    /**
     * Valuta uplate; null = osnovna valuta aplikacije (i tada je iznos uračunat u [CiljStednje.ustedjenoCenti]).
     * Uplate u drugim valutama čuvaju se u najmanjim jedinicama te valute i preračunavaju po današnjem tečaju.
     */
    val valuta: String? = null,
    /** [Transakcija.grupaId] prijenosa s računa (ili na račun) koji je stvorio ovu uplatu; null = ručna uplata. */
    val grupaId: Long? = null,
)

/**
 * Novčanik štednje u jednoj valuti (euro, strana valuta ili kriptovaluta).
 * Stanje je zbroj promjena; iznosi su u najmanjim jedinicama valute (vidi [hr.smartbudget.app.domain.Valuta]).
 */
@Entity(tableName = "novcanici")
data class Novcanik(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val naziv: String,
    val valuta: String,
    val redoslijed: Int = 0,
    /** Rok štednje u novčaniku (opcionalno), npr. kad se kripto planira prodati; aplikacija podsjeća prije isteka. */
    val rokEpochDan: Long? = null,
)

/** Uplata (pozitivna) ili isplata (negativna) u novčaniku. */
@Entity(tableName = "promjene_novcanika", indices = [Index("novcanikId")])
data class PromjenaNovcanika(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novcanikId: Long,
    val jedinice: Long,
    val epochDan: Long,
    /** Koliko je kupnja koštala u osnovnoj valuti aplikacije (opcionalno, za dobit/gubitak kod kriptovaluta). */
    val ulozenoCenti: Long? = null,
    val napomena: String = "",
    /** [Transakcija.grupaId] prijenosa s računa (ili na račun) koji je stvorio ovu promjenu; null = ručna promjena. */
    val grupaId: Long? = null,
)

/**
 * Ulaganje u vlasništvu korisnika (dionica, ETF, obveznica ili roba). Kriptovalute su u novčanicima.
 * Vrijednost se računa iz tržišne cijene ([simbol] je oznaka na Yahoo Financeu) i preračunava u valutu aplikacije.
 */
@Entity(tableName = "pozicije")
data class Pozicija(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val simbol: String,
    val naziv: String,
    /** [VrstaUlaganja] po imenu (DIONICE, ETF, OBVEZNICE, ROBA). */
    val vrsta: String,
    /** Valuta u kojoj kotira (USD, EUR, GBp…); kod ručnog unosa valuta iznosa. */
    val valuta: String,
    val redoslijed: Int = 0,
    /**
     * Ručni unos državnog zapisa ili obveznice koja nema tržišnu cijenu (npr. trezorski zapis RH "RHMF-T-722N"):
     * iznos koji se dobiva pri dospijeću (centi). Vrijednost raste od uloženog prema tom iznosu do [dospijeceEpochDan].
     */
    val nominalCenti: Long? = null,
    /** Rok ulaganja ili dospijeće (opcionalno za sva ulaganja); aplikacija podsjeća prije isteka. */
    val dospijeceEpochDan: Long? = null,
) {
    val rucno: Boolean get() = nominalCenti != null
}

/** Kupnja (pozitivna količina) ili prodaja (negativna) ulaganja. */
@Entity(tableName = "promjene_pozicija", indices = [Index("pozicijaId")])
data class PromjenaPozicije(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pozicijaId: Long,
    /** Količina u milijuntinkama (dopušteni su dijelovi dionice): 1,5 → 1 500 000. */
    val kolicinaMikro: Long,
    val epochDan: Long,
    /** Koliko je kupnja koštala u osnovnoj valuti aplikacije (opcionalno, za dobit/gubitak). */
    val ulozenoCenti: Long? = null,
    val napomena: String = "",
)

/** Stanje jednog računa iz transakcija (bez početnog stanja). */
data class ZbrojRacuna(val nacinPlacanja: String, val stanje: Long)

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
