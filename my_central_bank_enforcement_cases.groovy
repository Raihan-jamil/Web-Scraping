package current_scripts

import com.rdc.importer.scrapian.ScrapianContext
import com.moodys.kyc.textract.http.client.TextractClient
import com.moodys.kyc.textract.http.model.TextractResponse
import com.rdc.importer.scrapian.model.StringSource
import com.rdc.importer.scrapian.util.ModuleLoader
import com.rdc.scrape.ScrapeAddress
import com.rdc.scrape.ScrapeEvent
//import me.afifaniks.parsers.TessPDFParser

import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver

context.setup([connectionTimeout: 20000, socketTimeout: 25000, retryCount: 1, multithread: true, userAgent: "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36"])
context.session.encoding = "UTF-8" //change it according to web page's encoding
context.session.escape = true

My_central_bank_enforcement_cases script = new My_central_bank_enforcement_cases(context)
script.initParsing()

class My_central_bank_enforcement_cases {
    final entityType
    final addressParser
    ScrapianContext context = new ScrapianContext()
//    TessPDFParser pdfParser = new TessPDFParser()
    def moduleLoaderVersion = context.scriptParams.moduleLoaderVersion
    final def moduleFactory = ModuleLoader.getFactory(moduleLoaderVersion)

    final def root = "https://www.bnm.gov.my"
    static def url = "https://www.bnm.gov.my/cases-under-investigation"

    My_central_bank_enforcement_cases(context) {
        this.context = context
        entityType = moduleFactory.getEntityTypeDetection(context)
        addressParser = moduleFactory.getGenericAddressParser(context)

    }

    def initParsing() {

        // Declaring and setting the chrome driver path
        String CHROME_DRIVER_PATH = "C:\\Users\\Asus\\Downloads\\chromedriver_win32\\chromedriver.exe"
        System.setProperty("webdriver.chrome.driver", CHROME_DRIVER_PATH)


// Initializing the ChromeDriver
        WebDriver webDriver = new ChromeDriver()

        webDriver.get(url)

        // Get the HTML content of the link
        String html = webDriver.getPageSource()

        //def html = invoke(url)
        // println(html)
//        def pdfurl, pdfText
//        def pdfUrlMatcher = html =~ /(?ism)financial system.+?"(.+?)">Regulatees/
//        if (pdfUrlMatcher.find()) {
//            pdfurl = root + pdfUrlMatcher.group(1)
//            pdfText = pdfToTextConverter(pdfurl)
//            println(pdfText)
//            //  getDataFromPdf(pdfText)
//        }
        Non_Regulatees_data()
        regulatees_data()
    }

    def getDataFromPdf(pdfText) {
        def rowData
        def rowMatcher = pdfText =~ /(?ism)^(.+?document)/
        while (rowMatcher.find()) {
            def alias, name
            def remarkList = []
            def reasonList = []
            def dateList = []
            def url
            rowData = sanitizeRow(rowMatcher.group(0))

            def dateMatcher = rowData =~ /(?ism)\s\s(\d{1,2}\\/\d{1,2}\\/\d{4})\s\s/
            while (dateMatcher.find()) {
                dateList.add(dateMatcher.group(1))
            }

            def allMatcher = rowData =~ /(?ism)\s\s\d{1,2}\/\d{1,2}\/\d{4}\s\s+(.+?)\s\s.+?@@@@(.+?)\s\s.+?####(.+?)\s\s.+?document/
            if (allMatcher.find()) {
                name = allMatcher.group(1)
                remarkList.add(allMatcher.group(2))
                remarkList.each {
                    if (it.contains("####")) {
                        remarkList = it.split("####").collect({ its -> return its })
                    }
                }
                reasonList.add(allMatcher.group(3))
                reasonList.each {
                    if (it.contains("aaaa")) {
                        reasonList = it.split("aaaa").collect({ its -> return its })
                    }
                }
                def urlMatcher = rowData =~ /(?ism)url: (.+?)\s\s/
                if (urlMatcher.find()) {
                    url = urlMatcher.group(1)
                }
                createEntity(name, reasonList, dateList, remarkList, alias, url)
            }
        }
    }

    def regulatees_data() {
        def catchRowMatcher
        def lostDataList = new String[3]
        lostDataList[0] = "14 Jan 2019    J.P. Morgan Chase Bank Berhad     Administrative Monetary Penalty RM2,700,000;Order to do – To conduct a holistic review on the adequacy and effectiveness of its internal SCEL policy and procedures    Failure to comply with the single counterparty exposure limit"
        lostDataList[1] = "22 Feb 2019;17 May 2019    CIMB Bank Berhad    Compound – RM6,400,000;Administrative Monetary Penalty – RM3,400,000      Disclosure of customer information to third party;Failure to comply with standards issued by the Bank"
        lostDataList[2] = "22 Feb 2019;17 May 2019    CIMB Islamic Bank Berhad     Compound – RM3,200,000; Administrative Monetary Penalty – RM1,700,000     Disclosure of customer information to third party;Failure to comply with standards issued by the Bank"
        for (def i = 0; i < lostDataList.length; i++) {
            def url = "https://www.bnm.gov.my/documents/20124/62604/12072019_master.pdf"
            def dateList = []
            def entityName, alias
            def remarkList = []
            def reasonList = []


            if ((catchRowMatcher = lostDataList[i] =~ /(?ism)^(.+?)\s\s+(.+?)\s\s+(.+?)\s\s+(.+?)\u0024/))
                dateList.add(catchRowMatcher.group(1))
            dateList.each {
                if (it.contains(";")) {
                    dateList = it.split(";").collect({ its -> return its })
                }
            }
            entityName = catchRowMatcher.group(2)
            reasonList.add(catchRowMatcher.group(3))
            reasonList.each {
                if (it.contains(";")) {
                    reasonList = it.split(";").collect({ its -> return its })
                }
            }

            remarkList.add(catchRowMatcher.group(4))
            remarkList.each {
                if (it.contains(";")) {
                    remarkList = it.split(";").collect({ its -> return its })
                }
            }
            createEntity(entityName, reasonList, dateList, remarkList, alias, url)

        }

    }

    def Non_Regulatees_data() {
        def catchRowMatcher
        def lostDataList = new String[14]
        lostDataList[0] = "04 Aug 2020  Genneva Malaysia Sdn Bhd (GMSB)"
        lostDataList[1] = "04 Dec 2019  Bank Negara Malaysia (BNM)"
        lostDataList[2] = "23 Oct 2019  RSI International Berhad"
        lostDataList[3] = "22 Aug 2019  MGSB Berhad"
        lostDataList[4] = "01 Aug 2019  Bank Negara Malaysia (BNM)"
        lostDataList[5] = "03 May 2019  Bank Negara Malaysia (BNM)"
        lostDataList[6] = "07 Mar 2019  Bank Negara Malaysia (BNM)"
        lostDataList[7] = "12 Feb 2019  Bank Negara Malaysia (BNM)"
        lostDataList[8] = "24 May 2018  Axios Group Sdn Bhd "
        lostDataList[9] = "24 Apr 2018  Bank Negara Malaysia (BNM)"
        lostDataList[10] = "21 Feb 2018  Genneva Sdn. Bhd. (GSB)"
        lostDataList[11] = "28 Nov 2017  Koperasi Dinar Dirham Berhad"
        lostDataList[12] = "15 Sep 2017  MGSB Berhad led"
        lostDataList[13] = " 17 Aug 2017  Bank Negara Malaysia"

        for (def i = 0; i < lostDataList.length; i++) {
            def url = "https://www.bnm.gov.my/non-regulatees"
            def remarkList = []
            def reasonList = []
            def entityName, alias
            def dateList = []
            if ((catchRowMatcher = lostDataList[i] =~ /(?ism)^(.+?)\s\s(.+)/))
                dateList.add(catchRowMatcher.group(1))
            entityName = catchRowMatcher.group(2)
            if (entityName.toString().contains("(")) {
                def aliasRegex = entityName =~ /(?ism)\((.+?)\)/
                if (aliasRegex.find()) {
                    alias = aliasRegex.group(1)
                    entityName = entityName.toString().replaceAll(/$alias/, "").replaceAll(/\(/, "").replaceAll(/\)/, "").trim()
                    alias = alias.toString().replaceAll(/(?ism)(|)/, "")
                }
            }
            createEntity(entityName, reasonList, dateList, remarkList, alias, url)
        }
    }

    def createEntity(def entityName, def reasonList, def dateList, def remarkList, def alias, def suburl) {

        def entity = null
        entity = context.findEntity([name: entityName])
        if (!entity) {
            entity = context.getSession().newEntity()
            entity.setName(entityName)
            entity.setType("O")
        }

        if (suburl) {
            entity.addUrl(suburl)
        }

        def description = "This entity appears on the Central Bank of Malaysia list of cases involving companies and individuals who are being investigated by the bank."
        if (reasonList) {
            reasonList.each { a ->
                if (a) {
                    def description2 = description + " Action Taken: " + a
                    dateList.each { b ->
                        if (b) {
                            ScrapeEvent event = new ScrapeEvent()
                            b = context.parseDate(new StringSource(b), ["dd/MM/yyyy", "dd MMM yyyy"] as String[])
                            event.setDate(b)
                            event.setDescription(description2.toString().replaceAll(/(?s)\s+/, " ").trim())
                            entity.addEvent(event)
                        }
                    }
                }
            }
        } else {
            dateList.each {
                if (it) {
                    ScrapeEvent event = new ScrapeEvent()
                    it = context.parseDate(new StringSource(it), ["dd/MM/yyyy", "dd MMM yyyy"] as String[])
                    event.setDate(it)
                    event.setDescription(description.toString().replaceAll(/(?s)\s+/, " ").trim())
                    entity.addEvent(event)
                }
            }
        }
        if (remarkList) {
            remarkList.each {
                if (it) {
                    entity.addRemark(it.toString().replaceAll(/(?s)\s+/, " ").trim())
                }
            }
        }
        if (alias) {
            entity.addAlias(alias)
        }

        ScrapeAddress scrapeAddress = new ScrapeAddress()
        scrapeAddress.setCountry("Malaysia")
        entity.addAddress(scrapeAddress)
    }

    def sanitizeRow(rowData) {
        rowData = rowData.toString().replaceAll(/(?ism)(?:\u000CNo.|enforcement).+?date of .+?press.+?taken/, "")
        rowData = rowData.toString().replaceAll(/(?ism)^(.+?)(the institu.+?)(\s\s.+?\s\s)((?:taken\s)*remedial ste.+?)(\s\s.+?\s\s)((?:its expos|ensure the).+?)(\s\s.+?\s\s)((?:regulatory|customer info).+?)(\s\s+.+)/, { def a, b, c, d, e, f, g, h, i, j -> return b + c + " " + e + " " + g + " " + i + d + f + h + j })
                .replaceAll(/(?ism)^(.+?\s\s)(J\.P\.)(\s\s.+?\s\s)(Morgan)(\s\s.+?\s\s)(Chase)(\s\s.+?\s\s)(bank)(\s\s.+?\s\s)(Berhad)(\s+.+)/, { def a, b, c, d, e, f, g, h, i, j, k, l -> return b + c + " " + e + " " + g + " " + i + " " + k + d + f + h + j + l })
                .replaceAll(/(?ism)^(.+?\s\s)(CIMB)(\s\s.+?\s\s)(Islamic)(\s\s.+?\s\s)(Bank)(\s\s.+?\s\s)(Berhad)(\s\s.+)/, { def a, b, c, d, e, f, g, h, i, j -> return b + c + " " + e + " " + g + " " + i + d + f + h + j })
                .replaceAll(/(?ism)^(.+?\s\s)(CIMB)(\s\s.+?\s\s)(Bank)(\s\s.+?\s\s)(Berhad)(\s\s.+)/, { def a, b, c, d, e, f, g, h -> return b + c + " " + e + " " + g + d + f + h })
                .replaceAll(/(?ism)^(.+?)(Failure to)(.+?)(comply wit.+?)(\s\s.+?)(single)(.+?)(counterparty)(.+?)(exposure.+?)(\s\s.+)/, { def a, b, c, d, e, f, g, h, i, j, k, l -> return b + " @@@@ " + c + " " + e + " " + g + " " + i + " " + k + d + f + h + j + l })
                .replaceAll(/(?ism)^(.+?)(Disclosure of)(.+?)(customer)(.+?)(information to)(.+?)(third pa.+?)(\s\s.+?)(Failure to)(.+?)(comply.+?)(\s\s.+?)(standards)(.+?)(issued by.+?)(\s\s.+?)(Bank)(.+)/, '$1  @@@@ $2 $4 $6 $8 #### $10 $12 $14 $16 $18   $3$5$7$9$11$13$15$17$19 ')
                .replaceAll(/(?ism)^(.+?)(Administrative)(\s\s.+?)(Monetary.+?)(\s\s.+?)(RM2,70.+?)(\s\s.+?)(Order to.+?)(\s\s.+?)(conduct a.+?)(\s\s.+?)(review on.+?)(\s\s.+?)(adequacy and)(.+?)(effectiveness.+?)(\s\s.+?)(internal.+?)(\s\s.+?)(and proce.+?)(\s\s.+)/, '$1  #### $2 $4 $6 aaaa $8 $10 $12 $14 $16 $18 $20  $3$5$7$9$11$13$15$17$19$21')
                .replaceAll(/(?ism)^(.+?)(Compound.+?)(\s\s.+?)(RM\d{1,2},\d{2,},\d.+?)(\s\s.+?)(Administrative)(.+?)(Monetary Pe.+?)(\s\s.+?)(RM\d{1,2},\d{2,}.+?)(\s\s.+)/, { def a, b, c, d, e, f, g, h, i, j, k, l -> return b + "#### " + c + " " + e + " aaaa " + g + " " + i + " " + k + d + f + h + j + l })
                .replaceAll(/(?ism)P\.N\.02\/2019/, "url: https://www.bnm.gov.my/index.php?rp=ea%20cimb#gsc.tab=0")
                .replaceAll(/(?ism)P\.N\.01\/2019/, "url: https://www.bnm.gov.my/index.php?rp=ea%20jp%20morgan#gsc.tab=0")
        return rowData
    }

    def invoke(url, cache = false, tidy = false) {
        return context.invoke([url: url, tidy: tidy, cache: cache])
    }


    TextractResponse pdfToTextConverter(def pdfUrl) {
        def pdfFile = invokeBinary(pdfUrl)
        def bytes = pdfFile.getValue()
        TextractClient textractClient = new TextractClient(context.getGridDataServiceUrl(), context.getOauth2Username(), context.getOauth2Password(), context.getOauth2Url())
        TextractResponse text = textractClient.detectDocumentText(bytes)
        return text
    }

    def invokeBinary(url, type = null, paramsMap = null, cache = false, clean = true, miscData = [:]) {
        //Default type is GET
        Map dataMap = [url: url, type: type, params: paramsMap, clean: clean, cache: cache]
        dataMap.putAll(miscData)
        return context.invokeBinary(dataMap)
    }

}

