package hr.smartbudget.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransakcijaDao {
    /** Transakcije u rasponu [od, doIskljucivo), silazno po datumu. */
    @Transaction
    @Query("SELECT * FROM transakcije WHERE datumVrijeme >= :od AND datumVrijeme < :doIskljucivo ORDER BY datumVrijeme DESC, id DESC")
    fun promatrajRaspon(od: Long, doIskljucivo: Long): Flow<List<TransakcijaSKategorijom>>

    @Transaction
    @Query("SELECT * FROM transakcije WHERE datumVrijeme >= :od AND datumVrijeme < :doIskljucivo ORDER BY datumVrijeme ASC, id ASC")
    suspend fun dohvatiRaspon(od: Long, doIskljucivo: Long): List<TransakcijaSKategorijom>

    /** Kumulativno stanje: svi prihodi ikada minus svi troškovi ikada (centi). */
    @Query("SELECT COALESCE(SUM(CASE WHEN tip = 'PRIHOD' THEN iznosCenti ELSE -iznosCenti END), 0) + (SELECT COALESCE(SUM(pocetnoStanjeCenti), 0) FROM racuni) FROM transakcije")
    fun promatrajUkupnoStanje(): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN tip = 'PRIHOD' THEN iznosCenti ELSE -iznosCenti END), 0) + (SELECT COALESCE(SUM(pocetnoStanjeCenti), 0) FROM racuni) FROM transakcije")
    suspend fun ukupnoStanje(): Long

    /** Stanja po računima (bez početnog stanja). */
    @Query("SELECT nacinPlacanja, COALESCE(SUM(CASE WHEN tip = 'PRIHOD' THEN iznosCenti ELSE -iznosCenti END), 0) AS stanje FROM transakcije GROUP BY nacinPlacanja")
    fun promatrajPoRacunima(): Flow<List<ZbrojRacuna>>

    /** Sve transakcije (pretraga kroz sve cikluse, godišnji pregled). */
    @Transaction
    @Query("SELECT * FROM transakcije ORDER BY datumVrijeme DESC, id DESC")
    fun promatrajSve(): Flow<List<TransakcijaSKategorijom>>

    /** Zadnji nazivi za prijedloge pri unosu. */
    @Transaction
    @Query("SELECT * FROM transakcije WHERE opis != '' AND jePrijenos = 0 ORDER BY datumVrijeme DESC LIMIT 400")
    fun promatrajZadnje(): Flow<List<TransakcijaSKategorijom>>

    @Query("SELECT * FROM transakcije WHERE grupaId = :grupa")
    suspend fun uGrupi(grupa: Long): List<Transakcija>

    @Query("DELETE FROM transakcije WHERE grupaId = :grupa")
    suspend fun obrisiGrupu(grupa: Long)

    @Query("UPDATE transakcije SET nacinPlacanja = :novi WHERE nacinPlacanja = :stari")
    suspend fun preimenujRacun(stari: String, novi: String)

    @Query("SELECT COUNT(*) FROM transakcije WHERE nacinPlacanja = :naziv")
    suspend fun brojZaRacun(naziv: String): Int

    /** Postoji li već ista transakcija (uvoz izvoda: duplikati). */
    @Query("SELECT COUNT(*) FROM transakcije WHERE datumVrijeme >= :od AND datumVrijeme < :doIskljucivo AND iznosCenti = :iznos AND tip = :tip")
    suspend fun brojIstih(od: Long, doIskljucivo: Long, iznos: Long, tip: TipTransakcije): Int

    /** Stanje svih transakcija prije zadanog trenutka (početno stanje za tijek novca). */
    @Query("SELECT COALESCE(SUM(CASE WHEN tip = 'PRIHOD' THEN iznosCenti ELSE -iznosCenti END), 0) + (SELECT COALESCE(SUM(pocetnoStanjeCenti), 0) FROM racuni) FROM transakcije WHERE datumVrijeme < :prije")
    fun promatrajStanjePrije(prije: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transakcije WHERE kategorijaId = :kategorijaId")
    suspend fun brojZaKategoriju(kategorijaId: Long): Int

    @Upsert
    suspend fun spremi(t: Transakcija): Long

    // Sigurnosna kopija
    @Query("SELECT * FROM transakcije ORDER BY id")
    suspend fun sve(): List<Transakcija>

    @Insert
    suspend fun umetni(lista: List<Transakcija>)

    @Query("DELETE FROM transakcije")
    suspend fun obrisiSve()

    @Delete
    suspend fun obrisi(t: Transakcija)
}

@Dao
interface KategorijaDao {
    @Query("SELECT * FROM kategorije ORDER BY tip DESC, naziv COLLATE NOCASE")
    fun promatrajSve(): Flow<List<Kategorija>>

    @Query("SELECT * FROM kategorije WHERE id = :id")
    suspend fun dohvati(id: Long): Kategorija?

    @Upsert
    suspend fun spremi(k: Kategorija): Long

    @Query("SELECT * FROM kategorije ORDER BY id")
    suspend fun sve(): List<Kategorija>

    @Insert
    suspend fun umetni(lista: List<Kategorija>)

    @Query("DELETE FROM kategorije")
    suspend fun obrisiSve()

    @Delete
    suspend fun obrisi(k: Kategorija)
}

@Dao
interface PonavljajuciDao {
    @Transaction
    @Query("SELECT * FROM ponavljajuci_unosi ORDER BY aktivan DESC, danUMjesecu, id")
    fun promatrajSve(): Flow<List<PonavljajuciSKategorijom>>

    @Query("SELECT * FROM ponavljajuci_unosi WHERE aktivan = 1")
    suspend fun dohvatiAktivne(): List<PonavljajuciUnos>

    @Query("SELECT COUNT(*) FROM ponavljajuci_unosi WHERE kategorijaId = :kategorijaId")
    suspend fun brojZaKategoriju(kategorijaId: Long): Int

    @Upsert
    suspend fun spremi(p: PonavljajuciUnos): Long

    @Query("SELECT * FROM ponavljajuci_unosi ORDER BY id")
    suspend fun sve(): List<PonavljajuciUnos>

    @Insert
    suspend fun umetni(lista: List<PonavljajuciUnos>)

    @Query("DELETE FROM ponavljajuci_unosi")
    suspend fun obrisiSve()

    @Delete
    suspend fun obrisi(p: PonavljajuciUnos)
}

@Dao
interface PrilagodbaDao {
    @Query("SELECT * FROM prilagodbe_ciklusa")
    fun promatrajSve(): Flow<List<PrilagodbaCiklusa>>

    @Query("SELECT * FROM prilagodbe_ciklusa")
    suspend fun sve(): List<PrilagodbaCiklusa>

    @Upsert
    suspend fun spremi(p: PrilagodbaCiklusa)

    @Query("DELETE FROM prilagodbe_ciklusa WHERE sidro = :sidro")
    suspend fun obrisi(sidro: String)

    @Insert
    suspend fun umetni(lista: List<PrilagodbaCiklusa>)

    @Query("DELETE FROM prilagodbe_ciklusa")
    suspend fun obrisiSve()
}

@Dao
interface RacunDao {
    @Query("SELECT * FROM racuni ORDER BY redoslijed, id")
    fun promatrajSve(): Flow<List<Racun>>

    @Query("SELECT * FROM racuni ORDER BY redoslijed, id")
    suspend fun sve(): List<Racun>

    @Upsert
    suspend fun spremi(r: Racun): Long

    @Delete
    suspend fun obrisi(r: Racun)

    @Insert
    suspend fun umetni(lista: List<Racun>)

    @Query("DELETE FROM racuni")
    suspend fun obrisiSve()
}

@Dao
interface CiljDao {
    @Query("SELECT * FROM ciljevi ORDER BY rokEpochDan IS NULL, rokEpochDan, id")
    fun promatrajSve(): Flow<List<CiljStednje>>

    @Query("SELECT * FROM ciljevi")
    suspend fun sve(): List<CiljStednje>

    @Upsert
    suspend fun spremi(c: CiljStednje): Long

    @Delete
    suspend fun obrisi(c: CiljStednje)

    @Insert
    suspend fun umetni(lista: List<CiljStednje>)

    @Query("DELETE FROM ciljevi")
    suspend fun obrisiSve()
}

@Dao
interface UplataDao {
    @Query("SELECT * FROM uplate_cilja ORDER BY epochDan DESC, id DESC")
    fun promatrajSve(): Flow<List<UplataCilja>>

    @Query("SELECT * FROM uplate_cilja")
    suspend fun sve(): List<UplataCilja>

    @Insert
    suspend fun umetni(u: UplataCilja): Long

    @Insert
    suspend fun umetni(lista: List<UplataCilja>)

    @Delete
    suspend fun obrisi(u: UplataCilja)

    @Query("DELETE FROM uplate_cilja WHERE ciljId = :ciljId")
    suspend fun obrisiZaCilj(ciljId: Long)

    @Query("DELETE FROM uplate_cilja")
    suspend fun obrisiSve()
}

@Dao
interface NovcanikDao {
    @Query("SELECT * FROM novcanici ORDER BY redoslijed, id")
    fun promatrajSve(): Flow<List<Novcanik>>

    @Query("SELECT * FROM novcanici")
    suspend fun sve(): List<Novcanik>

    @Upsert
    suspend fun spremi(n: Novcanik): Long

    @Delete
    suspend fun obrisi(n: Novcanik)

    @Insert
    suspend fun umetni(lista: List<Novcanik>)

    @Query("DELETE FROM novcanici")
    suspend fun obrisiSve()

    @Query("SELECT * FROM promjene_novcanika ORDER BY epochDan DESC, id DESC")
    fun promatrajPromjene(): Flow<List<PromjenaNovcanika>>

    @Query("SELECT * FROM promjene_novcanika")
    suspend fun svePromjene(): List<PromjenaNovcanika>

    @Insert
    suspend fun umetniPromjenu(p: PromjenaNovcanika): Long

    @Insert
    suspend fun umetniPromjene(lista: List<PromjenaNovcanika>)

    @Delete
    suspend fun obrisiPromjenu(p: PromjenaNovcanika)

    @Query("DELETE FROM promjene_novcanika WHERE novcanikId = :id")
    suspend fun obrisiPromjeneZa(id: Long)

    @Query("DELETE FROM promjene_novcanika")
    suspend fun obrisiSvePromjene()
}

@Dao
interface PostavkeDao {
    @Query("SELECT * FROM postavke_ciklusa WHERE id = 1")
    fun promatraj(): Flow<PostavkeCiklusa?>

    @Query("SELECT * FROM postavke_ciklusa WHERE id = 1")
    suspend fun dohvati(): PostavkeCiklusa?

    @Upsert
    suspend fun spremi(p: PostavkeCiklusa)
}

@Dao
interface PozicijaDao {
    @Query("SELECT * FROM pozicije ORDER BY redoslijed, id")
    fun promatrajSve(): Flow<List<Pozicija>>

    @Query("SELECT * FROM pozicije")
    suspend fun sve(): List<Pozicija>

    @Upsert
    suspend fun spremi(p: Pozicija): Long

    @Delete
    suspend fun obrisi(p: Pozicija)

    @Insert
    suspend fun umetni(lista: List<Pozicija>)

    @Query("DELETE FROM pozicije")
    suspend fun obrisiSve()

    @Query("SELECT * FROM promjene_pozicija ORDER BY epochDan DESC, id DESC")
    fun promatrajPromjene(): Flow<List<PromjenaPozicije>>

    @Query("SELECT * FROM promjene_pozicija")
    suspend fun svePromjene(): List<PromjenaPozicije>

    @Insert
    suspend fun umetniPromjenu(p: PromjenaPozicije): Long

    @Insert
    suspend fun umetniPromjene(lista: List<PromjenaPozicije>)

    @Delete
    suspend fun obrisiPromjenu(p: PromjenaPozicije)

    @Query("DELETE FROM promjene_pozicija WHERE pozicijaId = :id")
    suspend fun obrisiPromjeneZa(id: Long)

    @Query("DELETE FROM promjene_pozicija")
    suspend fun obrisiSvePromjene()
}
