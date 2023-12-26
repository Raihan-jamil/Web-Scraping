package current_scripts


import com.rdc.importer.scrapian.ScrapianContext
import com.rdc.importer.scrapian.model.StringSource
import com.rdc.importer.scrapian.util.ModuleLoader
import com.rdc.scrape.ScrapeAddress
import com.rdc.scrape.ScrapeEvent
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.support.ui.WebDriverWait

import java.util.concurrent.TimeUnit

context.setup([connectionTimeout: 25000, socketTimeout: 25000, retryCount: 10, multithread: true, userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36"])
context.session.encoding = "UTF-8"; //change it according to web page's encoding
context.session.escape = true;

CALIFORNIA_OFFENDERS script = new CALIFORNIA_OFFENDERS(context)

script.initParsing()
/**
 * The website contains search limit and session timing. Recaptcha has to be solved multiple times.
 * Use the wait and sleep timing accurately to avoid session timed out error.
 * Take a 30s~1m break after each failure. The scrapper is semi-automatic hence.
 * Use VPN upon getting blocked.
 * */

class CALIFORNIA_OFFENDERS {
    final ScrapianContext context

    def moduleLoaderVersion = context.scriptParams.moduleLoaderVersion
    final def moduleFactory = ModuleLoader.getFactory(moduleLoaderVersion)
    static def root = "https://www.meganslaw.ca.gov"
    static def url = "https://www.meganslaw.ca.gov"
    def searchUrl = "https://www.meganslaw.ca.gov/Search.aspx"
    final addressParser
    final entityType
    final CAPTCHA_SOLVE_TIMEOUT = 35000
    String currentCity = ""
    def memo = new File("lastName.txt")
    File countyFolder = null
    int currentNo = 0
    class ChromeDriverEngine {
        String CHROME_DRIVER_PATH = "/usr/bin/chromedriver"

        ChromeOptions options
        WebDriver driver

        ChromeDriverEngine() {
            System.setProperty("webdriver.chrome.driver", CHROME_DRIVER_PATH)

            options = new ChromeOptions()
            options.addArguments(
//                    "--headless",
                    "--disable-gpu",
                    "--ignore-certificate-errors",
                    "--window-size=1500,1500",
                    "--silent",
//                    "--blink-settings=imagesEnabled=false" // Don't load images
            )
            driver = new ChromeDriver(options)
            Thread.sleep(5000);
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)

        }

        WebElement waitTobeVisible(String xpath) {
            WebDriverWait wait = new WebDriverWait(driver, 15)
            wait.until(ExpectedConditions.elementToBeClickable(new By.ByXPath(xpath)))
        }

        WebElement get_DOM_element(String xpath) {
            try {
                return driver.findElement(By.xpath(xpath))
            } catch (org.openqa.selenium.ElementNotVisibleException ex) {
                println("Waiting...")
                waitTobeVisible(xpath)
                return driver.findElement(By.xpath(xpath))
            }

        }
    }
    CALIFORNIA_OFFENDERS(context) {
        this.context = context
        entityType = moduleFactory.getEntityTypeDetection(context)
        addressParser = moduleFactory.getGenericAddressParser(context)
        //addressParser.reloadData()
    }


    def initParsing() {
        ChromeDriverEngine driverEngine = new ChromeDriverEngine()
        context.info("Invoking $searchUrl")
        driverEngine.driver.get(searchUrl)
        Thread.sleep(25000)
        getData(driverEngine, true, null)

    }


    def getData(ChromeDriverEngine engine, def wait, def startingPoint) {
//        startingPoint = "MARTINEZ, PHILLIP ANDREW"
        if (wait)
            solveCaptcha(engine)
        println("CLICKING OPTIONS:")
        WebElement countryClick = engine.get_DOM_element("//*[@id=\"ui-id-13\"]").click()
        Thread.sleep(2000)
        def options = engine.get_DOM_element("//*[@id=\"OCounty\"]")
        Select objSelect = new Select(options)
        def countyList = objSelect.getOptions()
        println("SIZE:"+countyList.size())
// Iterate through the countyList starting from the 29th county to the 58th county
        int startIndex = 18 // Index of the 29th county 31, 33, 34, 35, 36, 37
        int endIndex = 58   // Index of the 58th county
        for (def i = startIndex; i <= endIndex; i++) {
            def element = countyList[i]
            currentNo = 0
            String county = element.text
            println(county)
            objSelect.selectByVisibleText(county)
            Thread.sleep(5000)
            currentCity = county
            context.info("Current County : $currentCity")
            countyFolder = new File("/home/ankan/Documents/californiaSexOff/$currentCity")
            if (countyFolder.exists()) {
//                countyFolder.delete()
            } else {
                countyFolder.mkdir()
            }
            WebElement searchClick = engine.get_DOM_element("//*[@id=\"ui-id-14\"]/input")
            searchClick.click()
            Thread.sleep(20000)
            if (engine.driver.findElements(By.xpath("//*[@id=\"slickgrid_848746oName\"]")).size() == 0) {
                WebElement showListbtn = engine.get_DOM_element("//*[@id=\"ShowListButton\"]/a").click()
                Thread.sleep(50000)
                //println("CLICKED LIST\n")
            }
            JavascriptExecutor jse = (JavascriptExecutor) engine.driver
            getOffenders(engine, jse, startingPoint)
            // readFiles()
            // countyFolder.delete()
            Thread.sleep(15000)
        }
    }


    def getOffenders(ChromeDriverEngine engine, JavascriptExecutor jse, String previous_name) {
        //println("GETTING OFFENDERS")
        int round = 0
        //println("PN: $previous_name")
        def start = false
        WebElement ELEMENT = null
        WebElement search = engine.get_DOM_element("//*[@id=\"SearchCount\"]")
        int searchCount = Integer.parseInt(search.getText().replaceAll(/(?ism)^(.+?)(\d+)$/, '$2'))
        //println("C: $currentNo S: $searchCount")
        if (previous_name == null) {
            start = true
        }
        memo.write("")
        round = searchCount / 10
        //println("TOTAL: $searchCount ROUNDS: $round")
        int r = 1, s = 1
        //scrolling startpoint

        while (!start) {
            //println("NOT FOUND YET. Parsed: $s")
            List<WebElement> namesColumn = engine.driver.findElements(By.cssSelector(".slick-cell.r1.l1"))
            for (int n = 0; n < namesColumn.size(); n++) {
                ELEMENT = namesColumn.get(n)
                def name = ELEMENT.getText()
                name = name.replaceAll(/(?ism)\bMore\b.*/, "").trim()
                s++
                // //println(name)
                if (name =~ /(?ism).*$previous_name.*/) {
                    //println("FOUND: $name")
                    start = true
                    scrollToElement(ELEMENT, jse, engine)
                    break
                }
            }
            scrollToElement(ELEMENT, jse, engine)
        }
        while (start && r < round) {
            List<WebElement> namesColumn = engine.driver.findElements(By.cssSelector(".slick-cell.r1.l1"))
            int lastIndx = namesColumn.size() - 1
            //   //println("LI: $lastIndx\n")
            ELEMENT = namesColumn.get(0);
            def lastoffenders = ELEMENT.getText()
            lastoffenders = lastoffenders.replaceAll(/(?sm)\r*\n\s*.+/, "").trim()
            //println("STARTING: $lastoffenders")
            int i = 1

            for (WebElement data : namesColumn) {
                String name = ""
                try {
                    name = data.getText()
                    name = name.replaceAll(/(?ism)\bMore\b.*/, "").trim()
                    //println("NAME: $name")
                    lastoffenders = name
                    getTablet(engine, data, i, name)
                    ELEMENT = data
                    memo.append("COMPLETED UPTO:\n$name\n")
                    i++
                } catch (org.openqa.selenium.WebDriverException e) {
                    //println("GOT EXP @ $name")
                    if (currentNo < searchCount) {
                        //    solveCaptcha(engine)
                        getOffenders(engine, jse, lastoffenders)
                    }
                } catch (org.openqa.selenium.StaleElementReferenceException e) {
                    Thread.sleep(10000)
                    if (currentNo < searchCount) {
                        scrollToElement(ELEMENT, jse, engine)
                    }
                }
            }
            scrollToElement(ELEMENT, jse, engine)
            r++
        }
    }

    def scrollToElement(WebElement ELEMENT, JavascriptExecutor jse, ChromeDriverEngine engine) {
        try {
            jse.executeScript("arguments[0].scrollIntoView();", ELEMENT);
            Thread.sleep(15000)
        }
        catch (Exception e) {
            Thread.sleep(15000)
            jse.executeScript("arguments[0].scrollIntoView();", ELEMENT);
        } catch (Exception e) {
            engine.driver.get(searchUrl)
            getData(engine, true)
        }
    }

    def getTablet(ChromeDriverEngine engine, WebElement data, int index, String name) {
        WebElement tab = data.findElement(By.linkText("More Info"))
        tab.click()
        Thread.sleep(5000)
        String fileName = "/home/ankan/Documents/californiaSexOff/$currentCity/$currentCity-offender-$name" + ".txt"
        File offenderTable = new File(fileName)
        offenderTable.write("")
        offenderTable.append(name + "\n\n")
        def tableData = engine.get_DOM_element("//*[@id=\"OffenderPop\"]/table")
        tableData = tableData.getAttribute("innerHTML")
        offenderTable.append(tableData + "\n==================\n")
        currentNo++
        println("TABLEDATA COMPLETED\n*****")
        //  readFiles()
//        WebElement xBtn = engine.driver.findElement(By.xpath("/html/body/div[3]/div[1]/button"))
        WebElement xBtn = engine.driver.findElement(By.cssSelector("span.ui-button-icon-primary.ui-icon.ui-icon-closethick"))
        xBtn.click()
    }

    def solveCaptcha(ChromeDriverEngine engine) {
        context.info("SOLVE CAPTCHA")
        Thread.sleep(30000)

    }
    /**
     * Read the saved files from your saved directory.
     * Use Java File for this task.
     **/

    def readFiles() {
        // final File folder = new File("/home/jasmin/County/aaaaaaa")
        listFilesForFolder(countyFolder);
    }

    public void listFilesForFolder(final File folder) {
        int i = 0
        for (final File fileEntry : folder.listFiles()) {
            i++
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry);
            } else {
                String html = fileEntry.text
                getEntityData(html)
            }
        }
    }

    def getEntityData(def html) {
        def rowData
        def rowMatcher = html =~ /(?ism)^.+?<tbody><tr>\s+<td valign.+?<\\/tbody><\\/table>/
        while (rowMatcher.find()) {
            rowData = rowMatcher.group(0)
            def aliasList = []
            def scarsList = []
            def name, dob, sex, height, weight, eye, race, hair, scars, eventDate, description, address

            def nameMatcher = html =~ /(?ism)^(.+?)<tbody><tr>\s+<td valign=/
            if (nameMatcher.find()) {
                name = nameMatcher.group(1)
                        .replaceAll(/(?ism)DAVIS III, JR/, "DAVIS III")
                name = name.toString().replaceAll(/RABAGO, JR/, "RABAGO")
                name = name.toString().replaceAll(/(?ism)\s(JR|sr)\s*$/, "")
                name = name.toString().replaceAll(/(?s)\s+/, " ").trim()
                name = name.toString().replaceAll(/(?ism)(.+?)(,)(.+?$)/, { def a, b, c, d -> return d + " " + b })
            }


            def aliasRexexMatcher = rowData =~ /(?ism)"PAliasList"><ul><li>(.*?)<\\/li><\\/ul><\\/td>/
            if (aliasRexexMatcher.find()) {
                aliasList.add(sanitizeAlias(aliasRexexMatcher.group(1)))
            }
            aliasList.each {
                if (it.contains("aaaaa")) {
                    aliasList = it.split("aaaaa").collect({ its -> return its })
                }
            }

            def dobMatcher = rowData =~ /(?ism)"PDOB">(.*?)<\/td>/
            if (dobMatcher.find()) {
                dob = dobMatcher.group(1)
            }
            def sexMatcher = rowData =~ /(?ism)"PSex">(.*?)<\/td>/
            if (sexMatcher.find()) {
                sex = sanitizeSex(sexMatcher.group(1))
            }

            def HeightMatcher = rowData =~ /(?ism)"PHeight">(.*?)<\\/td>/
            if (HeightMatcher.find()) {
                height = HeightMatcher.group(1)
            }

            def WeightMatcher = rowData =~ /(?ism)"PWeight">(.*?)<\\/td>/
            if (WeightMatcher.find()) {
                weight = WeightMatcher.group(1)
            }

            def eyeMatcher = rowData =~ /(?ism)"PEyeColor">(.*?)<\\/td>/
            if (eyeMatcher.find()) {
                eye = sanitizeData(eyeMatcher.group(1))
            }

            def raceMatcher = rowData =~ /(?ism)"PEthnicity">(.*?)<\\/td>/
            if (raceMatcher.find()) {
                race = sanitizeData(raceMatcher.group(1))
            }

            def hairMatcher = rowData =~ /(?ism)"PHairColor">(.*?)<\\/td>/
            if (hairMatcher.find()) {
                hair = hairMatcher.group(1)
            }


            def scarsMatcher = rowData =~ /(?ism)"PSMTs"><ul><li>(.*?)<\/li><\/ul>/
            if (scarsMatcher.find()) {
                scars = sanitizeScars(scarsMatcher.group(1))
                scarsList.add(scars)
            }

            scarsList.each {
                if (it.contains("aaaaa")) {
                    scarsList = it.split("aaaaa").collect({ its -> return its })
                }
            }
            def dateMatcher = rowData =~ /(?ism)PLastConviction_0">\s*(\d{4})\s*<\\/td>/
            if (dateMatcher.find()) {
                eventDate = dateMatcher.group(1).trim()
                eventDate = "01/01/" + eventDate

            }
            def descriptionList = []


            def desMatcher = rowData =~ /(?ism)PDescription_0">(.*?)<\\/td>.+?PDescription_1">(.+?)</
            if (desMatcher.find()) {
                descriptionList.add(desMatcher.group(1))
                descriptionList.add(desMatcher.group(2))
            }

            def desMatcher1 = rowData =~ /(?ism)PDescription_\d">(.*?)<\/td>/
            if (desMatcher1.find()) {
                descriptionList.add(sanitizeDes(desMatcher1.group(1)))
            }

            def addressMatcher = rowData =~ /(?ism)"PAddress_LKA">(.*?)<div>/
            if (addressMatcher.find()) {
                address = sanitizeaddress(addressMatcher.group(1))
            }
            createEntity(name, aliasList, dob, sex, height, weight, eye, race, hair, scars, eventDate, descriptionList, address, scarsList)
        }

    }


    def sanitizeAlias(def alias) {
        alias = alias.toString().replaceAll(/(?ism)<\/li><li>/, " aaaaa  ")
                .replaceAll(/(?ism)NONE, KASHIE NONE/, "KASHIE")
                .replaceAll(/(?ism)MONIKER, NONE/, "MONIKER")
                .replaceAll(/(?ism)OTHERDOB, NONE/, "OTHERDOB")
                .replaceAll(/(?ism)SSN, NONE/, "SSN")
                .replaceAll(/(?ism)NONE, KNOWN/, "")
                .replaceAll(/(?ism)AWEST, NONE/, "AWEST")
                .replaceAll(/(?ism)(\w+)(, NONE)/, { def a, b, c -> return b })
        alias = alias.toString().replaceAll(/(?ism)^\s*,|NONE, NONE /, "").trim()
        alias = alias.toString().replaceAll(/(?ism)None|NOTED,/, "")
        alias = alias.toString().replaceAll(/(?s)\s+/, " ").trim()
        return alias
    }

    def sanitizeScars(def scars) {
        scars = scars.toString().replaceAll(/(?ism)<\/li><li>/, "aaaaa")
        scars = scars.toString().replaceAll(/(?ism)None|(?ism), \$|\t\t/, "")
        scars = scars.toString().replaceAll(/(?ism)&amp;/, "&")
        scars = scars.toString().replaceAll(/(?ism)SCAR,\t/, "SCAR").trim()
        scars = scars.toString().replaceAll(/(?ism)NIG\*\*/, "NIG")
        scars = scars.toString().replaceAll(/(?ism),\t/, "")
        return scars
    }

    def sanitizeSex(def sex) {
        sex = sex.toString().replaceAll(/(?s)\s+/, " ").trim()
                .replaceAll(/UNKNOWN/, "")

        return sex
    }

    def sanitizeDes(def des) {
        des = des.toString().replaceAll(/(?s)\s+/, " ").trim()
                .replaceAll(/UNKNOWN/, "")
                .replaceAll(/(?i)\+\s*$/, "+.")

        return des
    }

    def sanitizeData(def data) {
        data = data.toString().replaceAll(/(?s)\s+/, " ").trim()
                .replaceAll(/UNKNOWN/, "")


        return data
    }

    def sanitizeaddress(def address) {
        address = address.toString()
                .replaceAll(/UNKNOWN/, "")
                .replaceAll(/(?s)\s+/, " ").trim()
        address = address.toString().replaceAll(/(?ism)^(.+?)$/, { def a, b -> return b + ", USA" })

        return address
    }

    def sanitizealiasList(def alias) {
        alias = alias.toString().replaceAll(/(?ism)(.+?)(,)(.+?$)/, { def a, b, c, d -> return d + " " + b })
                .replaceAll(/(?s)\s+/, " ").trim()
                .replaceAll(/, $/, "")
                .replaceAll(/(?ism)\s(JR|sr)\s*$/, "").trim()

        return alias
    }


    def createEntity(def entityName, def aliasList, def dob, def sex, def height, def weight, def eye, def race, def hair, def scars, def eventDate, def descriptionList, def address, def scarsList) {
        def entity = null
        entity = context.findEntity([name: entityName])
        if (!entity) {
            entity = context.getSession().newEntity()
            entity.setName(entityName)
            entity.setType("P")
        }

        if (aliasList) {
            aliasList.each {
                it = sanitizealiasList(it)
                if (!it.toString().isEmpty()) {
                    entity.addAlias(it)
                }
            }
        }


        ScrapeEvent event = new ScrapeEvent()
        if (descriptionList) {
            descriptionList.each {
                if (!it.toString().isEmpty()) {
                    event.setDescription(it)
                }
            }
        }

        eventDate = context.parseDate(new StringSource(eventDate), ["MM/dd/yyyy"] as String[])
        event.setDate(eventDate)
        entity.addEvent(event)

        if (dob) {
            entity.addDateOfBirth(dob)
        }
        if (eye) {
            entity.addEyeColor(eye)
        }

        if (height) {
            entity.addHeight(height)
        }
        if (hair) {
            entity.addHairColor(hair)
        }

        if (weight) {
            entity.addWeight(weight)
        }
        if (sex) {
            entity.addSex(sex)
        }
        if (race) {
            entity.addRace(race)
        }
        if (scarsList) {
            scarsList.each {
                if (!it.toString().isEmpty()) {
                    entity.addScarsMarks(it.toString().replaceAll(/(?s)\s+/, " ").trim())
                }
            }
        }
        if (address) {
            def addrMap = addressParser.parseAddress([text: address, force_country: true])
            ScrapeAddress scrapeAddress = addressParser.buildAddress(addrMap, [street_sanitizer: street_sanitizer])
            if (scrapeAddress) {
                entity.addAddress(scrapeAddress)
            }
        }
        //println("ENTITY: $entity.name\n")
    }

    def street_sanitizer = { street ->
        fixStreet(street)
    }

    def fixStreet(street) {
        street = street.toString().replaceAll(/(?s)\s{2,}/, " ").trim().replaceAll(/(?s)\s+/, " ").trim()
        return street
    }
}