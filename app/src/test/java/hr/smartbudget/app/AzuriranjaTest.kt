package hr.smartbudget.app

import hr.smartbudget.app.data.Azuriranja
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AzuriranjaTest {
    @Test
    fun kodVerzije() {
        assertEquals(3000, Azuriranja.kodVerzije("3.000"))
        assertEquals(3001, Azuriranja.kodVerzije("v3.001"))
        assertEquals(2900, Azuriranja.kodVerzije(" 2.900 "))
        assertNull(Azuriranja.kodVerzije("3.1"))
        assertNull(Azuriranja.kodVerzije("3.000.1"))
        assertNull(Azuriranja.kodVerzije("nova"))
    }

    @Test
    fun obaveznoSamoKodPromjeneZa01() {
        // +0,001: može se odbiti
        assertFalse(Azuriranja.obavezno(3000, 3001))
        assertFalse(Azuriranja.obavezno(3000, 3099))
        // +0,1 (i više): obavezno
        assertTrue(Azuriranja.obavezno(3000, 3100))
        assertTrue(Azuriranja.obavezno(3099, 3100))
        assertTrue(Azuriranja.obavezno(2900, 3000))
        assertTrue(Azuriranja.obavezno(2950, 4000))
    }

    @Test
    fun parsiraGithubIzdanje() {
        val json = """
            {"tag_name":"v3.001","draft":false,"prerelease":false,"body":"- Ispravak\r\n- Novo",
             "assets":[{"name":"izmjene.txt","browser_download_url":"https://x/t","size":1},
                       {"name":"SmartBudget-3.001.apk","browser_download_url":"https://x/a.apk","size":5000000}]}
        """.trimIndent()
        val n = Azuriranja.parsiraj(json)!!
        assertEquals("3.001", n.naziv)
        assertEquals(3001, n.kod)
        assertEquals("- Ispravak\n- Novo", n.izmjene)
        assertEquals("https://x/a.apk", n.apkUrl)
        assertEquals(5_000_000L, n.velicina)
    }

    @Test
    fun izdanjeBezApkaIliNacrtSeIgnorira() {
        assertNull(Azuriranja.parsiraj("""{"tag_name":"3.001","assets":[]}"""))
        assertNull(Azuriranja.parsiraj("""{"tag_name":"3.001","draft":true,"assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
        assertNull(Azuriranja.parsiraj("""{"tag_name":"beta","assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
    }
}
