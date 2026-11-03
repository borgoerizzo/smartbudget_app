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
    @Query("SELECT COALESCE(SUM(CASE WHEN tip = 'PRIHOD' THEN iznosCenti ELSE -iznosCenti END), 0) FROM transakcije")
    fun promatrajUkupnoStanje(): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN tip = 'PRIHOD' THEN iznosCenti ELSE -iznosCenti END), 0) FROM transakcije")
    suspend fun ukupnoStanje(): Long

    /** Stanje svih transakcija prije zadanog trenutka (početno stanje za tijek novca). */
    @Query("SELECT COALESCE(SUM(CASE WHEN tip = 'PRIHOD' THEN iznosCenti ELSE -iznosCenti END), 0) FROM transakcije WHERE datumVrijeme < :prije")
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
interface PostavkeDao {
    @Query("SELECT * FROM postavke_ciklusa WHERE id = 1")
    fun promatraj(): Flow<PostavkeCiklusa?>

    @Query("SELECT * FROM postavke_ciklusa WHERE id = 1")
    suspend fun dohvati(): PostavkeCiklusa?

    @Upsert
    suspend fun spremi(p: PostavkeCiklusa)
}
