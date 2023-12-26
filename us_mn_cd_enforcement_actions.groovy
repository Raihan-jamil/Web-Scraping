package current_scripts

import com.rdc.importer.scrapian.ScrapianContext
import com.rdc.importer.scrapian.util.ModuleLoader
import com.rdc.scrape.ScrapeAddress
import com.rdc.scrape.ScrapeEvent
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.support.ui.WebDriverWait
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

context.setup([connectionTimeout: 20000, socketTimeout: 25000, retryCount: 3, multithread: true])
context.session.encoding = "UTF-8" //change it according to web page's encoding
context.session.escape = true

UsMnCdEnforcement script = new UsMnCdEnforcement(context)
script.initParsing()
script.checkMissingData()

class UsMnCdEnforcement {
    final ScrapianContext context
    def moduleLoaderVersion = context.scriptParams.moduleLoaderVersion
    final def moduleFactory = ModuleLoader.getFactory(moduleLoaderVersion)

    //a2d905c86b1424c8656d0432d41029977e4f81cd
    final entityType
    final String root = "https://www.cards.commerce.state.mn.us"
    final String url2 = root + "/CARDS/view/index.xhtml"
    def newDataSet = [] as Set
    def oldData = []

    // ========================================= CHROME DRIVER INITIALIZATION =========================================
    class ChromeDriverEngine {
        // String CHROME_DRIVER_PATH = "/home/mashturamazed/Documents/scraping/RDCScrapper/assets/selenium_driver/chromedriver"//"/usr/bin/chromedriver"
        String CHROME_DRIVER_PATH = "C:\\Users\\user\\Documents\\chromeDriver\\chromedriver-win64\\chromedriver.exe"

        ChromeOptions options
        WebDriver driver

        ChromeDriverEngine() {
            System.setProperty("webdriver.chrome.driver", CHROME_DRIVER_PATH)

            options = new ChromeOptions()
            options.addArguments(
                     "--headless",
                    //"--disable-gpu",
                    "--ignore-certificate-errors",
                    "--window-size=1366,768",
                    "--silent",
//                    "--blink-settings=imagesEnabled=false"
            )

            driver = new ChromeDriver(options)
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
        }

        void get(String URL, int sleepTime) {
            System.out.println("[SELENIUM] Invoking: [" + URL + "]")
            driver.get(URL)

            if (sleepTime != 0) {
                wait(sleepTime)
            }
        }

        WebElement findByXpath(String xpath) {
            try {
                By by = new By.ByXPath(xpath)
                return driver.findElement(by)
            } catch (Exception e) {
                return null
            }
        }

        void wait(int time) {
            try {
                Thread.sleep(time)
            } catch (InterruptedException e) {
                e.printStackTrace()
            }
        }

        void shutdown() {
            driver.close()
            driver.quit()
        }

        String getSource() {
            return driver.getPageSource()
        }

        void waitForElementToBeClickable(String xpath) {
            WebDriverWait wait = new WebDriverWait(driver, 60)
            wait.until(ExpectedConditions.elementToBeClickable(new By.ByXPath(xpath)))
        }
    }

    class ThreadExecutor implements Runnable {
        def industryValueList
        String key
        boolean isAlpha

        ThreadExecutor(def industryValueList, String key, boolean isAlpha) {
            this.industryValueList = industryValueList
            this.key = key
            this.isAlpha = isAlpha
        }

        @Override
        void run() {
            loadTableDataUsingSelenium(industryValueList, key, isAlpha)
        }
    }
// ========================================= CHROME DRIVER INITIALIZATION =========================================

    UsMnCdEnforcement(context) {
        this.context = context
        entityType = moduleFactory.getEntityTypeDetection(context)
        // entityType.addTokensintoFile(new File("orgTokensCleaned.txt"))
    }

//------------------------------Initial part----------------------//
    def initParsing() {
        def html = invoke(root)
        // println html
        def paramMap = getParamData(html)
        def newHtml = invokePost(root, paramMap)
        def industryType
        def stateValue

        // Capture All Industry Type.
        def industryTypeMatch = newHtml =~ /(?is)Industry type(.*?)\/select/
        if (industryTypeMatch.find()) {
            industryType = industryTypeMatch[0][1]
        }
        def industryValueList = []
        def industryValueMatch = industryType =~ /(?is)option\s*value="([^"]+)/
        while (industryValueMatch.find()) {
            industryValueList.add(industryValueMatch.group(1).trim())
        }

        // Capture All State Value
        def stateMatch = newHtml =~ /(?is)City.+?State(.*?)\/select/
        if (stateMatch.find()) {
            stateValue = stateMatch[0][1]
        }
        def stateValueList = []
        def stateValueMatch = stateValue =~ /(?is)option\s*value="([^"]+)/
        while (stateValueMatch.find()) {
            stateValueList.add(stateValueMatch.group(1).trim())
        }

        ExecutorService executorService = Executors.newFixedThreadPool(4)


        for (String name : stateValueList) {
            ThreadExecutor threadExecutor = new ThreadExecutor(null, name, true)
            executorService.execute(threadExecutor)
        }

        for (char cha = 'A'; cha <= 'Z'; cha++) {
            ThreadExecutor threadExecutor = new ThreadExecutor(null, String.valueOf(cha), true)
            executorService.execute(threadExecutor)
        }

        // Searching with keys
        for (String key : stateValueList) {
            if (key.equals("MN")) {
                ThreadExecutor threadExecutor = new ThreadExecutor(industryValueList, key, false)
                executorService.execute(threadExecutor)
            } else {
                ThreadExecutor threadExecutor = new ThreadExecutor(null, key, false)
                executorService.execute(threadExecutor)
            }
        }

        // Shutting down the service
        executorService.shutdown()

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (InterruptedException e) {
            e.printStackTrace()
        }

    }

    def checkMissingData() {
        String filePath = "C:\\Users\\user\\Documents\\Data\\OldData.xlsx"
        def missingDataList = []

        try {
            XSSFWorkbook workbook = new XSSFWorkbook(filePath)
            XSSFSheet sheet = workbook.getSheet("oldSheet")

            for (Row row : sheet) {
                if (newDataSet.add(row.getCell(0).toString().trim())) {
                    if (detectEntityType(row.getCell(0).toString()).equals("P")) {
                        missingDataList.add((row.getCell(0).toString().trim() =~ /(?i)(.+?)(\s[.\w\\\/\(\)\']+$)/)[0][1])
                    } else {
                        missingDataList.add(row.getCell(0).toString().trim())
                    }
                }
            }

            ExecutorService executorService = Executors.newFixedThreadPool(4)

            missingDataList.each { name ->
                ThreadExecutor threadExecutor = new ThreadExecutor(null, name, true)
                executorService.execute(threadExecutor)
            }
            executorService.shutdown()
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
            } catch (InterruptedException e) {
                e.printStackTrace()
            }

        } catch (Exception e) {
            e.printStackTrace()
        }

    }

    def loadTableDataUsingSelenium(def industryValueList, String key, boolean isAlpha) {
        ChromeDriverEngine engine = new ChromeDriverEngine()

        engine.get('https://www.cards.commerce.state.mn.us', 5000)
        String optionElement = "//select[@id='form:docType']"

        engine.waitForElementToBeClickable(optionElement)

        // Select options
        Select options = new Select(engine.findByXpath(optionElement))
        options.selectByVisibleText("Enforcement Actions")

        if (isAlpha) {
            def respondentSelector = "//input[@id='form:criteria:2:text']"
            engine.waitForElementToBeClickable(respondentSelector)
            // Input Value
            println("Inputting Keys: $key")
            engine.findByXpath(respondentSelector).sendKeys(key)
            searchAndInvokePage(engine, key)
            engine.shutdown()
        } else {
//            def selectElement = "//select[@name='form:j_idt32:0:j_idt43']"
            println("Inputting Key: $key")
            if (industryValueList) {

                // Select State
                def selectElement = "//select[@id='form:criteria:4:dropdown']"
                engine.waitForElementToBeClickable(selectElement)
                Select keys = new Select(engine.findByXpath(selectElement))
                keys.selectByVisibleText(key)

                for (String industryKey : industryValueList) {
                    // Select Industry
                    def selectElement2 = "//select[@id='form:criteria:0:dropdown']"
                    engine.waitForElementToBeClickable(selectElement2)
                    Select keys2 = new Select(engine.findByXpath(selectElement2))
                    keys2.selectByVisibleText(industryKey)

                    if (industryKey.toString().equals("Insurance") || industryKey.toString().equals("Real Estate")) {

                        for (char cha = 'A'; cha <= 'Z'; cha++) {
                            // Putting Respondent Name
                            def respondentSelector = "//input[@id='form:criteria:2:text']"
                            engine.waitForElementToBeClickable(respondentSelector)
                            println("Inputting Name: $cha")
                            engine.findByXpath(respondentSelector).clear()
                            engine.findByXpath(respondentSelector).sendKeys(String.valueOf(cha))
                            searchAndInvokePage(engine, key)
                            engine.wait(2000)
                        }
                    } else {
                        def respondentSelector = "//input[@id='form:criteria:2:text']"
                        engine.waitForElementToBeClickable(respondentSelector)
                        engine.findByXpath(respondentSelector).clear()
                        searchAndInvokePage(engine, key)
                        engine.wait(2000)
                    }
                    //engine.wait(2000)
                }
                engine.shutdown()

            } else {
                def selectElement = "//select[@id='form:criteria:4:dropdown']"
                engine.waitForElementToBeClickable(selectElement)

                // Select State
                Select keys = new Select(engine.findByXpath(selectElement))
                keys.selectByVisibleText(key)
                searchAndInvokePage(engine, key)
                engine.shutdown()
            }
        }
    }

    def searchAndInvokePage(ChromeDriverEngine engine, key) {
        // Click GO button to Search
        engine.findByXpath("//input[@id='form:searchButton']").click()
        engine.wait(5000)

        def selectElement = "//*[@name='form:tbl_rppDD']"
        engine.waitForElementToBeClickable(selectElement)
        Select keys = new Select(engine.findByXpath(selectElement))
        keys.selectByVisibleText("100")
        engine.wait(2000)


        String html = engine.getSource()
        handleDetailsPage(html)


        def nextBtnDisabled = engine.findByXpath("//div[@id='form:tbl_paginator_bottom']//button[@id='form:tbl:nextPageButton' and @disabled='disabled']")
        engine.wait(200)

        while (nextBtnDisabled == null) {
            // Click Next Button
            //println "Next Page of Key : $key"
            engine.findByXpath("//div[@id='form:tbl_paginator_bottom']//button[@id='form:tbl:nextPageButton']").click()
            engine.wait(2000)

            // Wait until loading finishes
            while (engine.findByXpath("//body//img[@alt='Loading' and @style='display: none;']") == null) {
                engine.wait(2000)
            }

            html = engine.getSource()
            handleDetailsPage(html)
            nextBtnDisabled = engine.findByXpath("//div[@id='form:tbl_paginator_bottom']//button[@id='form:tbl:nextPageButton' and @disabled='disabled']")
        }

        //engine.shutdown()
    }

    def handleDetailsPage(srcText) {
        def documentLink, name, date, description, city, state, zip
        srcText = srcText.toString().replaceAll(/&lt;/, "<").replaceAll(/&gt;/, ">")
        def columnReg = /<td\s*role="gridcell".+?>(.*?)<\/td>/
        def colDocument = /<td\s*role="gridcell".+><a\s*href="([^}]+}).*?<\/a><\/td>/
        def rowMatch = srcText =~ /<tr\s*data(.*?)(?=<\/tr>)/
        while (rowMatch.find()) {
            def nameList = []
            def cityList = []
            def stateList = []
            def zipList = []
            def row = rowMatch.group(1)
            def columnMatch = row =~ /(?i)$colDocument$columnReg$columnReg$columnReg$columnReg$columnReg$columnReg$columnReg$columnReg$columnReg/
            if (columnMatch.find()) {
                documentLink = columnMatch[0][1]
                name = columnMatch[0][3]
                name.split(/<hr.*?>/).each {
                    it = it.toString() replaceAll(/(?i)(?<=ASSOCIATION)\sLP/, ", L. P.")
                    it = it.toString() replaceAll(/(?i)ACOCELLA\.\sFRANK/, "ACOCELLA, FRANK")
                    it = it.toString() replaceAll(/(?i)CONNERS\s*DARLENE/, "CONNERS, DARLENE")
                    nameList.add(it)
                }

                date = columnMatch[0][4]
                description = columnMatch[0][7]
                city = columnMatch[0][8]
                cityList = city.split(/<hr.*?>/)
                state = columnMatch[0][9]
                stateList = state.split(/<hr.*?>/)
                zip = columnMatch[0][10]
                zipList = zip.split(/<hr.*?>/)
            }

            createEntity(documentLink, nameList, date, description, cityList, stateList, zipList)
        }
    }

    def createEntity(documentLink, nameList, eventDate, description, cityList, stateList, zipList) {
        def entity

        context.info("Creating Entity: $nameList")

        nameList.eachWithIndex {
            name, index ->
                if (name) {
                    def checkName = sanitize(name)
                    def type = detectEntityType(name)

                    if (type.equals("P"))
                        checkName = personNameReformat(name)

                    entity = context.findEntity([name: checkName, type: type])

                    if (!entity) {
                        def aliasList = name.split(/(?i)(?:,?a\/k\/a|dba\/*|\baka\b)/).collect { it }
                        name = aliasList[0].toString().trim()

                        def aliasMatcher = name =~ /(?m)(?!\(MO\)|\(THE\))\(([\w]+)\)$/
                        if (aliasMatcher.find()) {
                            aliasList.add((sanitize(aliasMatcher.group(1))))
                            name = name.replaceAll(/(?m)(?!\(MO\)|\(THE\))\(([\w]+)\)$/, "")
                        }

                        name = name.toString().replaceAll(/\($/, "")
                        def zipKey = [name, type]
                        entity = context.findEntity(zipKey)
                        if (!entity) {
                            entity = context.newEntity(zipKey)
                            if (type.equals("P"))
                                name = personNameReformat(name)
                            newDataSet.add(name.toString())

                            entity.setName(sanitize(name))
                            entity.setType(type)
                            if (aliasList.size() > 1) {
                                aliasList = aliasList.toList()
                                aliasList.remove(0)
                                aliasList.each {
                                    if (it) {
                                        it = it.toString().replaceAll(/\(|\)/, "")
                                        entity.addAlias(sanitize(it))
                                    }

                                }
                            }
                        }
                    }

                    ScrapeAddress addressObj = new ScrapeAddress()
                    addressObj.setCountry("UNITED STATES")

                    if (nameList.size() > cityList.size()) {
                        addressObj.setCity(sanitize(cityList[0]))
                        addressObj.setPostalCode(sanitize(zipList[0]))
                        addressObj.setProvince(sanitize(stateList[0]))
                    } else {
                        addressObj.setCity(sanitize(cityList[index]))
                        if (zipList.size() == index) {
                            addressObj.setPostalCode(sanitize(zipList[index - 1]))
                        } else {
                            addressObj.setPostalCode(sanitize(zipList[index]))
                        }

                        addressObj.setProvince(sanitize(stateList[index]))
                    }
                    entity.addAddress(addressObj)

                    def link = root + documentLink

                    link = link.replaceAll(/\{/, "%7B")
                    link = link.replaceAll(/\}/, "%7D")

                    entity.addUrl(link)

                    def desc = "This entity appears on the Minnesota Commerce Department list of Enforcement Actions. Allegation: " + sanitize(description).trim()
                    desc = desc.replaceAll(/(?is)\s+|\:\W*$|\(|\?/, " ").trim()
                    desc = desc.replaceAll(/(?is)<hr\s?\/>/, "").trim()
                    desc = desc.replaceAll(/\$\s*$/, "").trim()
                    desc = desc.replaceAll(/(?s)\s+/, " ").trim()
                    ScrapeEvent event = new ScrapeEvent()
                    event.setDescription(desc)

                    if (eventDate != null)
                        event.setDate(eventDate)

                    event.setCategory("REG")
                    event.setSubcategory("ACT")
                    entity.addEvent(event)
                }

        }
    }

    def detectEntityType(name) {
        def type
        type = entityType.detectEntityType(name)

        if (name =~ /(?i)\b(DIVERSIFIED|R INS AGCY|WAREHOUSE|RENTER|MORTGAGE|INVESTIGATION[S]?|TRAINING|MANUFACTURER|WILDLIFE|TRADEMARK|AMERICAS|AMERIFED\sDOC\b|SPORTS|NEWSLETTER|MY\sCASH\sNOW|SAFE\sLOAN|LONDON|LINK|MEDICA\sSELF|STAMP|BROIT\sLIGHT|MODIFY|INTERNAITONAL|IT|FARMS|BANK|BONDS|HELPING|SCHOOL|CASCADAS\sMEXICO|MONEY)\b/) {
            type = "O"
        } else if (name =~ /(?i)\b(?:INVESTIGATIONS|DOLLAR TREE|HOUSE NANNY'S|ROOFING|METALS|MOTORS|LODGE|PAYDAY|COST CUTTERS|MOBIL|FITNESS EVOLUTION|GREEN ACRES|LOOK GOOD|FARM|MONETARY GOLD|LOAN NOW|COBB ADJUSTING|SMARTHEALTH|VACATION|LIVESTOCK|YOGURT SUNDAE|LINCOLN AND MORGAN|RENOVATIONS)\b/) {
            type = "O"
        } else if (name =~ /(?i)(BRIAN DEL|MAI PA|WEST, JAMES|ANDREA JO|LOAN H\b|HONG LOAN THI|DEL TERZO|MARK S\b|RUTH S\.J\.|JEREMY R\b|ANH T\b|TIEN T\b|RIDGE, MICHAEL|MICHAEL NASSIF)/) {
            type = "P"
        }

        if (type.equals("P")) {
            if (name =~ /(?i)(?:^\S+$)/)
                return "O"
        }
        return type
    }

    def personNameReformat(name) {
        return name.replaceAll(/(?i)^\s*([^,]+?),\s*([^,]+?),?(\s*[js]r\.?|\s*I{2,3})?$/, '$2 $1 $3').trim()
    }

//------------------------------Misc utils part---------------------//
    def invoke(url, isPost = false, cache = false, headersMap = [:], postParams = [:], cleanSpaceChar = false, tidy = false, miscData = [:]) {
        Map data = [url: url, tidy: tidy, headers: headersMap, cache: cache, clean: cleanSpaceChar]
        if (isPost) {
            data.type = "POST"
            data.params = postParams
        }
        data.putAll(miscData)
        return context.invoke(data)
    }

    def invokePost(def url, def paramsMap, def cache = false, def headersMap = [:], def tidy = true) {
        try {
            return context.invoke([url: url, tidy: tidy, type: "POST", params: paramsMap, headers: headersMap, cache: cache]);
        } catch (Exception e) {
            e.printStackTrace()
        }
    }


    def getParamData(def html, def optionValue = "", def isNext = "") {
        def map = [:]
        def source, vstate
        def viewStateMatch = html =~ /(?is)ViewState".*value="([^"]+)"/
        if (viewStateMatch) {
            vstate = viewStateMatch[0][1]
        } else if ((viewStateMatch = html =~ /(?ims)"form_SUBMIT".*?;([A-Z]+[^=]+=)/)) {
            vstate = viewStateMatch[0][1]
        } else if ((viewStateMatch = html =~ /(?ism)"form_SUBMIT".*?\/form&gt;(.*?)</)) {
            vstate = viewStateMatch[0][1].toString().trim()
            if (vstate != null)
                vstate = vstate.replaceAll(/(?ism)PrimeFaces\.scrollTo.*/, "")
        } else if ((viewStateMatch = html =~ /(?ism)\]\}\);\s*\\/\\/--&gt;&lt;\\/script&gt;(.*?)(PrimeFaces\.scrollTo)/)) {
            vstate = viewStateMatch[0][1].toString().trim()
        }

        def csrfMatch = html =~ /(?is)_csrf"\s*value="([^"]+)/
        def sourceMatch = html =~ /<select id="([^"]+)"/
        if (sourceMatch) {
            source = sourceMatch[0][1]
        } else if ((sourceMatch = html =~ /(?i)div class="well\b.*?input\s*id="([^"]+)"/)) {
            source = sourceMatch[0][1]
        }
        map["javax.faces.partial.ajax"] = "true"
        map["javax.faces.partial.render"] = "form"

        if (isNext) {
            map["javax.faces.partial.execute"] = "@all"
            sourceMatch = html =~ /button id="([^"]+)".*?Next/
            if (sourceMatch) {
                source = sourceMatch[0][1]
            }
            map[source] = source
            map["javax.faces.partial.render"] = "form:tbl"
            map["form:tbl:j_id1252080239_1_19954642"] = "25"

            map["form:tbl_columnOrder"] = "form:tbl:j_id1252080239_1_19954084:0,form:tbl:j_id1252080239_1_19954084:1,form:tbl:j_id1252080239_1_19954084:2,form:tbl:j_id1252080239_1_19954084:3,form:tbl:j_id1252080239_1_19954084:4,form:tbl:j_id1252080239_1_19954084:5,form:tbl:j_id1252080239_1_19954084:6,form:tbl:j_id1252080239_1_19954084:7,form:tbl:j_id1252080239_1_19954084:8,form:tbl:j_id1252080239_1_19954084:9"
        } else {
            map["javax.faces.partial.execute"] = "form:docType"
            if (optionValue) {
                map["javax.faces.behavior.event"] = "action"
            } else {
                map["javax.faces.behavior.event"] = "change"
            }
            if (optionValue) {
                map["javax.faces.partial.event"] = "click"
            } else {
                map["javax.faces.partial.event"] = "change"
            }
        }
        map["javax.faces.source"] = source
        map["form:docType"] = "1"
        //map["form_SUBMIT"] = "1"

        if (csrfMatch.find()) {
            map["_csrf"] = csrfMatch.group(1)
        }

        map["javax.faces.ViewState"] = vstate
        if (optionValue) {
            map["form:j_id1252080239_1_19954493:0:j_id1979103314_2_199547ee"] = optionValue
            map["form:j_id1252080239_1_19954493:1:j_id1979103314_1_1995440b"] = ""
            map["form:j_id1252080239_1_19954493:2:j_id1979103314_1_1995440b"] = ""
            map["form:j_id1252080239_1_19954493:3:j_id1979103314_1_1995440b"] = ""
            map["form:j_id1252080239_1_19954493:4:j_id1979103314_2_199547ee"] = ""
            map["form:j_id1252080239_1_19954493:5:j_id1979103314_1_1995440b"] = ""
            map["form:j_id1252080239_1_19954493:6:j_id1979103314_3_19954768_input"] = ""
            map["form:j_id1252080239_1_19954493:7:j_id1979103314_3_19954768_input"] = ""
            map["form:j_id1979103314_4_19954714"] = ""
            map["form:tbl:filter"] = ""
        }
        return map
    }

    def sanitize(data) {
        data = data.replaceAll(/C\/O.+/, "").trim()
        data = data.replaceAll(/&amp;/, '&').replaceAll(/,$/, "").replaceAll("\\u00ef", "i").replaceAll(/&quot;/, "\"").replaceAll(/;/, " ").replaceAll(/(?i)(?:N\/A|Unknown)/, "").replaceAll(/(\-|:|#)/, " ").replaceAll(/(?m),\s*$/, "").replaceAll(/(?s)\s{2,}/, " ").replaceAll(/(?m),\s*$/, "").replaceAll(/^(?:\"|\')/, "").replaceAll(/(?:\"|\')$/, "").trim()
        return data.replaceAll(/(?s)\s+|\*/, " ").trim()
    }
}