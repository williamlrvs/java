package com.br.SerasaScoreFetcher;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.NoSuchElementException;

import java.time.Duration;
import java.util.List;
import java.util.Scanner;

public class SerasaScoreFetcher {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite seu CPF (ex: 123.456.789-00): ");
        String cpf = scanner.nextLine().replaceAll("[^0-9]", "");
        System.out.print("Digite sua senha: ");
        String senha = scanner.nextLine();

        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            // MANTÉM VISÍVEL para resolver CAPTCHA manualmente
            // options.addArguments("--headless"); // Descomente SOMENTE após testar
            options.addArguments("--disable-blink-features=AutomationControlled"); // Reduz detecção de bot
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"); // User-agent real

            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            System.out.println("🔍 Acessando Serasa...");
            driver.manage().window().maximize();
            driver.get("https://www.serasa.com.br/entrar");

            // Aguarda carregamento
            Thread.sleep(3000);

            // DEBUG: Mostra inputs
            debugInputs(driver);

            // Passo 1: Preenche CPF
            System.out.println("📝 Preenchendo CPF...");
            WebElement cpfField = wait.until(ExpectedConditions.elementToBeClickable(By.id("f-cpf"))); // Usando ID do debug
            cpfField.clear();
            cpfField.sendKeys(cpf);

            // Clica em Continuar
            WebElement continueBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Continuar') or @type='submit']")
            ));
            System.out.println("🔘 Clicando em Continuar...");
            continueBtn.click();
            Thread.sleep(2000);

            // Passo 2: Preenche Senha
            System.out.println("🔐 Preenchendo Senha...");
            WebElement senhaField = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//input[@type='password']")));
            senhaField.clear();
            senhaField.sendKeys(senha);

            // Passo 3: Verifica se CAPTCHA apareceu ANTES de clicar em Entrar
            boolean captchaDetected = isCaptchaPresent(driver);
            if (captchaDetected) {
                System.out.println("⚠️ CAPTCHA detectado! Resolva no navegador aberto (clique em 'Não sou robô' ou similar).");
                System.out.println("Após resolver, pressione Enter aqui para continuar...");
                scanner.nextLine(); // Pausa até usuário resolver manualmente
            }

            // Passo 4: Clica em Entrar (ou tenta novamente após CAPTCHA)
            System.out.println("🚀 Tentando login...");
            WebElement loginBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(@class, 'btn-passwordless') or contains(text(), 'Entrar')]") // Usando class do erro
            ));
            loginBtn.click();
            Thread.sleep(5000);

            // Verifica se ainda há CAPTCHA (caso não resolvido)
            if (isCaptchaPresent(driver)) {
                System.out.println("⚠️ CAPTCHA ainda presente. Tente resolver novamente e pressione Enter.");
                scanner.nextLine();
            }

            // Passo 5: Verifica login e navega para Score
            String currentUrl = driver.getCurrentUrl();
            System.out.println("📍 URL atual: " + currentUrl);
            if (currentUrl.contains("entrar") || currentUrl.contains("login")) {
                System.out.println("❌ Falha no login. Verifique credenciais ou resolva manualmente.");
                System.out.println("Pressione Enter após corrigir no navegador...");
                scanner.nextLine();
            }

            System.out.println("📊 Navegando para Score...");
            driver.get("https://www.serasa.com.br/score");
            Thread.sleep(3000);

            // Passo 6: Extrai Score
            String score = extractScore(driver, wait);
            if (score != null) {
                System.out.println("🎉 Seu Serasa Score é: " + score);
            } else {
                System.out.println("❌ Score não encontrado. Debug:");
                debugScoreElements(driver);
                Thread.sleep(30000); // Tempo para inspecionar
            }

        } catch (Exception e) {
            System.out.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                System.out.println("🌐 Navegador mantido aberto para inspeção. Pressione Enter para fechar.");
                scanner.nextLine();
                driver.quit();
            }
            scanner.close();
        }
    }

    private static boolean isCaptchaPresent(WebDriver driver) {
        try {
            // Detecta iframe de CAPTCHA (DataDome)
            driver.findElement(By.xpath("//iframe[contains(@src, 'captcha-delivery.com') or contains(@id, 'ddChallenge')]"));
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private static void debugInputs(WebDriver driver) {
        System.out.println("🔍 DEBUG - Inputs encontrados:");
        List<WebElement> inputs = driver.findElements(By.tagName("input"));
        for (int i = 0; i < inputs.size(); i++) {
            WebElement input = inputs.get(i);
            String type = input.getAttribute("type") != null ? input.getAttribute("type") : "";
            String name = input.getAttribute("name") != null ? input.getAttribute("name") : "";
            String id = input.getAttribute("id") != null ? input.getAttribute("id") : "";
            String placeholder = input.getAttribute("placeholder") != null ? input.getAttribute("placeholder") : "";
            System.out.println(String.format("Input %d: type=%s, name=%s, id=%s, placeholder=%s", i, type, name, id, placeholder));
        }
    }

    private static String extractScore(WebDriver driver, WebDriverWait wait) {
        try {
            // Aguarda elemento do score
            WebElement scoreElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//*[contains(@class, 'score') and text()[normalize-space(.) != '']]")
            ));
            return scoreElement.getText().replaceAll("[^0-9]", "");
        } catch (Exception e) {
            // Fallback com JS
            JavascriptExecutor js = (JavascriptExecutor) driver;
            return (String) js.executeScript(
                    "var els = document.querySelectorAll('[class*=score]'); " +
                            "for (var el of els) { " +
                            "  var text = el.innerText.trim(); " +
                            "  if (text.match(/^\\d{3,4}$/)) return text; " +
                            "} return null;"
            );
        }
    }

    private static void debugScoreElements(WebDriver driver) {
        System.out.println("🔍 DEBUG - Elementos com 'score':");
        List<WebElement> elements = driver.findElements(By.xpath("//*[contains(@class, 'score') or contains(text(), 'Score')]"));
        for (WebElement el : elements) {
            System.out.println("Elemento: " + el.getTagName() + " | Class: " + el.getAttribute("class") + " | Text: " + el.getText());
        }
    }
}