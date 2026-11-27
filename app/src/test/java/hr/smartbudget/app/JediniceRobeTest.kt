package hr.smartbudget.app

import hr.smartbudget.app.domain.JediniceRobe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JediniceRobeTest {
    @Test fun zlatoPoGramu() {
        val c = JediniceRobe.cijene("GC=F", 3_110.34768, "USD").toMap()
        assertEquals(100.0, c.getValue("1 g"), 1e-9)
        assertEquals(100_000.0, c.getValue("1 kg"), 1e-6)
    }

    @Test fun naftaPoLitriIPsenicaUCentima() {
        assertEquals(0.5, JediniceRobe.cijene("CL=F", 79.4936, "USD").toMap().getValue("1 l"), 1e-4)
        // ZW=F kotira u centima po bušelu: 544,31 ¢ = 5,4431 $ / 27,2155 kg ≈ 0,20 $/kg
        assertEquals(0.2, JediniceRobe.cijene("ZW=F", 544.310844, "USX").toMap().getValue("1 kg"), 1e-6)
    }

    @Test fun ostaloBezPreracuna() {
        assertTrue(JediniceRobe.cijene("AAPL", 200.0, "USD").isEmpty())
    }
}
