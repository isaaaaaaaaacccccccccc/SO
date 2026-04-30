import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InterfaceGrafica: interface Swing para simular criancas e bolas com assets.
 */
public class InterfaceGrafica {
    private static final int CAPACIDADE_CESTO_PADRAO = 5;
    private static final int CAPACIDADE_CESTO_MINIMA = 1;
    private static final int KID_W = 76;
    private static final int KID_H = 92;
    private static final int BALL_SIZE = 26;
    private static final int CHEST_W = 128;
    private static final int CHEST_H = 104;

    private final Cesto cesto;
    private final List<Crianca> criancas;
    private final Map<Integer, PainelCrianca> paineisCriancas;
    private final Map<Integer, RegistroEstado> registrosEstado;

    private JFrame frame;
    private JTextArea logArea;
    private JLabel labelBolas;
    private JPanel painelCriancasContainer;
    private JSpinner spinnerTempo;
    private JSpinner spinnerDescanso;
    private JCheckBox checkboxPossuiBola;
    private PainelParque painelParque;

    private BufferedImage imagemKid;
    private BufferedImage imagemKid2;
    private BufferedImage imagemBola;
    private BufferedImage imagemGrama;
    private BufferedImage imagemCesto;

    private volatile boolean rodando = true;

    private static class RegistroEstado {
        private final Crianca.EstadoCrianca estado;
        private final long inicioMillis;

        RegistroEstado(Crianca.EstadoCrianca estado, long inicioMillis) {
            this.estado = estado;
            this.inicioMillis = inicioMillis;
        }
    }

    public InterfaceGrafica(Cesto cesto) {
        this.cesto = cesto;
        this.criancas = new ArrayList<>();
        this.paineisCriancas = new HashMap<>();
        this.registrosEstado = new HashMap<>();

        carregarAssets();
        criarInterface();
        iniciarThreadAtualizacaoUI();
        adicionarLog("OK: Cesto criado com capacidade " + cesto.getCapacidade());
    }

    private void carregarAssets() {
        imagemKid = carregarImagem("kid.png");
        imagemKid2 = carregarImagem("kid2.png");
        imagemBola = carregarImagem("ball.png");
        imagemGrama = carregarImagem("grass.png");
        imagemCesto = carregarImagem("chest.png");
    }

    private BufferedImage carregarImagem(String nome) {
        try (java.io.InputStream in = getClass().getResourceAsStream("/assets/" + nome)) {
            if (in != null) {
                return ImageIO.read(in);
            }
            return ImageIO.read(new File("assets", nome));
        } catch (IOException e) {
            System.err.println("Nao foi possivel carregar assets/" + nome + ": " + e.getMessage());
            return null;
        }
    }

    private void criarInterface() {
        frame = new JFrame("SO - Criancas, Bolas e Cesto");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 820);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);

        JPanel painelPrincipal = new JPanel(new BorderLayout(10, 10));
        painelPrincipal.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        painelPrincipal.setBackground(new Color(229, 233, 218));

        painelPrincipal.add(criarPainelSuperior(), BorderLayout.NORTH);
        painelPrincipal.add(criarPainelCentral(), BorderLayout.CENTER);
        painelPrincipal.add(criarPainelLog(), BorderLayout.SOUTH);

        frame.add(painelPrincipal);
        frame.setVisible(true);
    }

    private JPanel criarPainelSuperior() {
        JPanel painel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        painel.setBackground(new Color(42, 105, 84));
        painel.setBorder(BorderFactory.createLineBorder(new Color(26, 70, 56), 2));

        labelBolas = new JLabel("Cesto: 0/" + cesto.getCapacidade());
        labelBolas.setFont(new Font("Arial", Font.BOLD, 16));
        labelBolas.setForeground(Color.WHITE);
        painel.add(labelBolas);

        painel.add(criarSeparador());

        JLabel labelTempo = new JLabel("Tempo brincadeira (s):");
        labelTempo.setForeground(Color.WHITE);
        painel.add(labelTempo);

        spinnerTempo = new JSpinner(new SpinnerNumberModel(2, 1, null, 1));
        painel.add(spinnerTempo);

        JLabel labelDescanso = new JLabel("Tempo descanso (s):");
        labelDescanso.setForeground(Color.WHITE);
        painel.add(labelDescanso);

        spinnerDescanso = new JSpinner(new SpinnerNumberModel(1, 1, null, 1));
        painel.add(spinnerDescanso);

        checkboxPossuiBola = new JCheckBox("Possui bola inicialmente");
        checkboxPossuiBola.setBackground(new Color(42, 105, 84));
        checkboxPossuiBola.setForeground(Color.WHITE);
        painel.add(checkboxPossuiBola);

        JButton btnCriarCrianca = new JButton("+ Criar crianca");
        btnCriarCrianca.setFont(new Font("Arial", Font.BOLD, 12));
        btnCriarCrianca.setBackground(new Color(244, 197, 66));
        btnCriarCrianca.setForeground(new Color(35, 35, 35));
        btnCriarCrianca.addActionListener(e -> criarNovaCrianca());
        painel.add(btnCriarCrianca);

        return painel;
    }

    private JSeparator criarSeparador() {
        JSeparator separador = new JSeparator(JSeparator.VERTICAL);
        separador.setPreferredSize(new Dimension(1, 28));
        return separador;
    }

    private JPanel criarPainelCentral() {
        JPanel painel = new JPanel(new BorderLayout(10, 10));
        painel.setOpaque(false);

        painelParque = new PainelParque();
        painel.add(painelParque, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(criarPainelCriancas());
        scroll.setPreferredSize(new Dimension(310, 0));
        scroll.setBorder(BorderFactory.createTitledBorder("Threads"));
        painel.add(scroll, BorderLayout.EAST);

        return painel;
    }

    private JPanel criarPainelCriancas() {
        painelCriancasContainer = new JPanel();
        painelCriancasContainer.setLayout(new BoxLayout(painelCriancasContainer, BoxLayout.Y_AXIS));
        painelCriancasContainer.setBackground(new Color(248, 249, 244));
        return painelCriancasContainer;
    }

    private JPanel criarPainelLog() {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        logArea = new JTextArea(5, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Courier New", Font.PLAIN, 11));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        painel.add(scroll, BorderLayout.CENTER);

        return painel;
    }

    private void criarNovaCrianca() {
        int id = criancas.size() + 1;
        long tempoBrincadeiraSegundos = ((Number) spinnerTempo.getValue()).longValue();
        long tempoDescansoSegundos = ((Number) spinnerDescanso.getValue()).longValue();
        long tempoBrincadeira = tempoBrincadeiraSegundos * 1000L;
        long tempoDescanso = tempoDescansoSegundos * 1000L;
        boolean possuiBola = checkboxPossuiBola.isSelected();

        Crianca crianca = new Crianca(id, possuiBola, tempoBrincadeira, tempoDescanso, cesto);
        configurarCenaDaCrianca(crianca);
        criancas.add(crianca);
        atualizarVizinhosDasCriancas();

        PainelCrianca painelCrianca = new PainelCrianca(crianca);
        paineisCriancas.put(id, painelCrianca);
        painelCriancasContainer.add(painelCrianca);
        painelCriancasContainer.revalidate();
        painelCriancasContainer.repaint();

        synchronized (registrosEstado) {
            registrosEstado.put(id, new RegistroEstado(crianca.getEstado(), System.currentTimeMillis()));
        }

        crianca.setEstadoListener(c -> {
            registrarEventoEstado(c);
            atualizarVisualCrianca(c);
        });

        Thread thread = new Thread(crianca);
        thread.setName("Crianca-" + id);
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.start();

        adicionarLog("OK: Crianca #" + id + " criada (Tb=" + tempoBrincadeiraSegundos + "s, Td="
                + tempoDescansoSegundos + "s, Bola=" + possuiBola + ")");
    }

    private void configurarCenaDaCrianca(Crianca crianca) {
        if (painelParque == null) {
            return;
        }

        Rectangle cestoRect = painelParque.getCestoBounds();
        crianca.configurarCena(
                painelParque.getWidth(),
                painelParque.getHeight(),
                cestoRect.x,
                cestoRect.y
        );
    }

    private void configurarCenaDeTodasCriancas() {
        for (Crianca crianca : criancas) {
            configurarCenaDaCrianca(crianca);
        }
    }

    private void atualizarVizinhosDasCriancas() {
        List<Crianca> snapshot = new ArrayList<>(criancas);
        for (Crianca crianca : criancas) {
            crianca.setCriancasNoParque(snapshot);
        }
    }

    private void registrarEventoEstado(Crianca crianca) {
        String mensagemEvento = null;
        long agora = System.currentTimeMillis();

        synchronized (registrosEstado) {
            RegistroEstado anterior = registrosEstado.get(crianca.getId());
            Crianca.EstadoCrianca estadoAtual = crianca.getEstado();

            if (anterior == null) {
                registrosEstado.put(crianca.getId(), new RegistroEstado(estadoAtual, agora));
                return;
            }

            if (anterior.estado == estadoAtual) {
                return;
            }

            long duracaoMillis = Math.max(0, agora - anterior.inicioMillis);
            mensagemEvento = montarMensagemEvento(crianca.getId(), anterior.estado, duracaoMillis);
            registrosEstado.put(crianca.getId(), new RegistroEstado(estadoAtual, agora));
        }

        if (mensagemEvento != null) {
            adicionarLog(mensagemEvento);
        }
    }

    private String montarMensagemEvento(int id, Crianca.EstadoCrianca estadoAnterior, long duracaoMillis) {
        String duracao = formatarDuracaoSegundos(duracaoMillis);
        switch (estadoAnterior) {
            case BRINCANDO:
                return "Crianca #" + id + " brincou por " + duracao;
            case DESCANSANDO:
                return "Crianca #" + id + " descansou por " + duracao;
            case ESPERANDO_BOLA:
                return "Crianca #" + id + " esperou bola por " + duracao;
            case ESPERANDO_ESPACO:
                return "Crianca #" + id + " aguardou espaco no cesto por " + duracao;
            default:
                return null;
        }
    }

    private String formatarDuracaoSegundos(long duracaoMillis) {
        long segundos = Math.round(duracaoMillis / 1000.0);
        return segundos + (segundos == 1 ? " segundo" : " segundos");
    }

    private void atualizarVisualCrianca(Crianca crianca) {
        SwingUtilities.invokeLater(() -> {
            PainelCrianca painel = paineisCriancas.get(crianca.getId());
            if (painel != null) {
                painel.atualizar();
            }
            if (painelParque != null) {
                painelParque.repaint();
            }
        });
    }

    private void adicionarLog(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = String.format("[%02d:%02d:%02d] ",
                    System.currentTimeMillis() / 3600000 % 24,
                    System.currentTimeMillis() / 60000 % 60,
                    System.currentTimeMillis() / 1000 % 60);

            logArea.append(timestamp + mensagem + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());

            int linhas = logArea.getLineCount();
            if (linhas > 100) {
                try {
                    int endOfFirstLine = logArea.getLineEndOffset(0);
                    logArea.replaceRange("", 0, endOfFirstLine + 1);
                } catch (Exception e) {
                    // Ignora limpeza parcial do log.
                }
            }
        });
    }

    private void iniciarThreadAtualizacaoUI() {
        Thread updateThread = new Thread(() -> {
            try {
                while (rodando) {
                    Thread.sleep(250);
                    SwingUtilities.invokeLater(this::atualizarCesto);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        updateThread.setDaemon(true);
        updateThread.setPriority(Thread.NORM_PRIORITY);
        updateThread.start();
    }

    private void atualizarCesto() {
        try {
            int bolasAtuais = cesto.getBolasAtuais();
            labelBolas.setText("Cesto: " + bolasAtuais + "/" + cesto.getCapacidade());

            if (bolasAtuais == 0 || bolasAtuais == cesto.getCapacidade()) {
                labelBolas.setForeground(new Color(255, 224, 117));
            } else {
                labelBolas.setForeground(Color.WHITE);
            }

            if (painelParque != null) {
                painelParque.repaint();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class PainelParque extends JPanel {
        PainelParque() {
            setPreferredSize(new Dimension(850, 520));
            setMinimumSize(new Dimension(520, 320));
            setBackground(new Color(104, 162, 91));
            setBorder(BorderFactory.createLineBorder(new Color(67, 112, 72), 2));
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    configurarCenaDeTodasCriancas();
                }
            });
        }

        Rectangle getCestoBounds() {
            int x = Math.max(20, getWidth() - CHEST_W - 44);
            int y = Math.max(80, getHeight() / 2 - CHEST_H / 2);
            return new Rectangle(x, y, CHEST_W, CHEST_H);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            desenharGrama(g);
            desenharCesto(g);
            desenharCriancas(g);
            g.dispose();
        }

        private void desenharGrama(Graphics2D g) {
            if (imagemGrama != null) {
                for (int x = 0; x < getWidth(); x += imagemGrama.getWidth()) {
                    for (int y = 0; y < getHeight(); y += imagemGrama.getHeight()) {
                        g.drawImage(imagemGrama, x, y, null);
                    }
                }
            } else {
                g.setColor(new Color(110, 168, 94));
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            g.setColor(new Color(255, 255, 255, 120));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(18, 18, getWidth() - 36, getHeight() - 36, 24, 24);
        }

        private void desenharCesto(Graphics2D g) {
            Rectangle cestoRect = getCestoBounds();
            if (imagemCesto != null) {
                g.drawImage(imagemCesto, cestoRect.x, cestoRect.y, cestoRect.width, cestoRect.height, null);
            } else {
                g.setColor(new Color(122, 78, 41));
                g.fillRoundRect(cestoRect.x, cestoRect.y, cestoRect.width, cestoRect.height, 12, 12);
            }

            int bolasAtuais = 0;
            try {
                bolasAtuais = cesto.getBolasAtuais();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            for (int i = 0; i < bolasAtuais; i++) {
                int col = i % 4;
                int row = i / 4;
                int bx = cestoRect.x + 18 + col * 24;
                int by = cestoRect.y + 20 + row * 18;
                desenharBola(g, bx, by, 22);
            }

            g.setColor(new Color(25, 39, 34, 210));
            g.setFont(new Font("Arial", Font.BOLD, 13));
            g.drawString(bolasAtuais + "/" + cesto.getCapacidade(), cestoRect.x + 42, cestoRect.y + cestoRect.height + 18);
        }

        private void desenharCriancas(Graphics2D g) {
            List<Crianca> snapshot = new ArrayList<>(criancas);
            snapshot.sort(Comparator.comparingDouble(Crianca::getY));

            for (Crianca crianca : snapshot) {
                desenharCrianca(g, crianca);
            }
        }

        private void desenharCrianca(Graphics2D g, Crianca crianca) {
            int x = (int) Math.round(crianca.getX());
            int y = (int) Math.round(crianca.getY() + Math.sin(crianca.getPassoAnimacao()) * 3);

            g.setColor(new Color(0, 0, 0, 45));
            g.fillOval(x + 10, y + KID_H - 10, KID_W - 16, 14);

            BufferedImage spriteKid = (crianca.getEstado() == Crianca.EstadoCrianca.ESPERANDO_BOLA
                    && imagemKid2 != null) ? imagemKid2 : imagemKid;

            if (spriteKid != null) {
                if (crianca.isOlhandoParaDireita()) {
                    g.drawImage(spriteKid, x, y, KID_W, KID_H, null);
                } else {
                    AffineTransform transform = new AffineTransform();
                    transform.translate(x + KID_W, y);
                    transform.scale(-1, 1);
                    transform.scale((double) KID_W / spriteKid.getWidth(), (double) KID_H / spriteKid.getHeight());
                    g.drawImage(spriteKid, transform, null);
                }
            } else {
                g.setColor(new Color(84, 147, 203));
                g.fillOval(x + 14, y, 44, 44);
                g.fillRoundRect(x + 18, y + 42, 38, 42, 10, 10);
            }

            if (crianca.possuiBola()) {
                if (crianca.getEstado() == Crianca.EstadoCrianca.ESPERANDO_ESPACO) {
                    desenharBola(g, x + KID_W - 17, y + 42, BALL_SIZE);
                } else {
                    desenharBola(g, x + KID_W - 20, y + KID_H - 28, BALL_SIZE);
                }
            }

            desenharEtiqueta(g, crianca, x, y);
        }

        private void desenharBola(Graphics2D g, int x, int y, int tamanho) {
            if (imagemBola != null) {
                g.drawImage(imagemBola, x, y, tamanho, tamanho, null);
            } else {
                g.setColor(new Color(245, 78, 66));
                g.fillOval(x, y, tamanho, tamanho);
                g.setColor(Color.WHITE);
                g.drawOval(x + 4, y + 4, tamanho - 8, tamanho - 8);
            }
        }

        private void desenharEtiqueta(Graphics2D g, Crianca crianca, int x, int y) {
            String texto = "#" + crianca.getId() + " " + textoEstadoCurto(crianca.getEstado());
            Font fonte = new Font("Arial", Font.BOLD, 11);
            g.setFont(fonte);
            FontMetrics metrics = g.getFontMetrics();
            int largura = metrics.stringWidth(texto) + 12;
            int etiquetaX = Math.max(8, Math.min(x, getWidth() - largura - 8));
            int etiquetaY = Math.max(20, y - 8);

            g.setColor(corEstado(crianca.getEstado()));
            g.fillRoundRect(etiquetaX, etiquetaY - 17, largura, 20, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(texto, etiquetaX + 6, etiquetaY - 3);
        }
    }

    private class PainelCrianca extends JPanel {
        private final Crianca crianca;
        private final JLabel labelInfo;
        private final JLabel labelTempos;
        private final JLabel labelEstado;
        private final JLabel labelBola;
        private final JLabel labelBrincadeiras;

        PainelCrianca(Crianca crianca) {
            this.crianca = crianca;
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
            setLayout(new GridLayout(5, 1, 2, 2));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(213, 218, 207)),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));

            labelInfo = criarLabelStatus("Crianca #" + crianca.getId(), Font.BOLD);
            long tbSeg = Math.round(crianca.getTempoDebrincadeira() / 1000.0);
            long tdSeg = Math.round(crianca.getTempoDescanso() / 1000.0);
            labelTempos = criarLabelStatus("Tb=" + tbSeg + "s | Td=" + tdSeg + "s", Font.PLAIN);
            labelEstado = criarLabelStatus("", Font.PLAIN);
            labelBola = criarLabelStatus("", Font.PLAIN);
            labelBrincadeiras = criarLabelStatus("", Font.PLAIN);

            add(labelInfo);
            add(labelTempos);
            add(labelEstado);
            add(labelBola);
            add(labelBrincadeiras);

            atualizar();
        }

        private JLabel criarLabelStatus(String texto, int estilo) {
            JLabel label = new JLabel(texto);
            label.setFont(new Font("Arial", estilo, 12));
            label.setForeground(new Color(28, 35, 30));
            return label;
        }

        void atualizar() {
            setBackground(corEstadoClara(crianca.getEstado()));
            labelEstado.setText("Estado: " + textoEstado(crianca.getEstado()));
            labelBola.setText(crianca.possuiBola() ? "Bola: com bola" : "Bola: sem bola");
            labelBrincadeiras.setText("Brincou: " + crianca.getContagemBrincadeiras() + "x");
        }
    }

    private String textoEstado(Crianca.EstadoCrianca estado) {
        switch (estado) {
            case BRINCANDO:
                return "brincando";
            case ESPERANDO_BOLA:
                return "esperando bola";
            case ESPERANDO_ESPACO:
                return "esperando espaco";
            case DESCANSANDO:
                return "descansando";
            default:
                return "";
        }
    }

    private String textoEstadoCurto(Crianca.EstadoCrianca estado) {
        switch (estado) {
            case BRINCANDO:
                return "brinca";
            case ESPERANDO_BOLA:
                return "quer bola";
            case ESPERANDO_ESPACO:
                return "devolve";
            case DESCANSANDO:
                return "descansa";
            default:
                return "";
        }
    }

    private Color corEstado(Crianca.EstadoCrianca estado) {
        switch (estado) {
            case BRINCANDO:
                return new Color(40, 122, 188);
            case ESPERANDO_BOLA:
                return new Color(112, 118, 123);
            case ESPERANDO_ESPACO:
                return new Color(210, 120, 47);
            case DESCANSANDO:
                return new Color(94, 99, 106);
            default:
                return Color.DARK_GRAY;
        }
    }

    private Color corEstadoClara(Crianca.EstadoCrianca estado) {
        switch (estado) {
            case BRINCANDO:
                return new Color(218, 236, 250);
            case ESPERANDO_BOLA:
                return new Color(226, 228, 229);
            case ESPERANDO_ESPACO:
                return new Color(251, 234, 214);
            case DESCANSANDO:
                return new Color(232, 233, 235);
            default:
                return Color.WHITE;
        }
    }

    public static void mostraInterfaceGrafica() {
        SwingUtilities.invokeLater(() -> {
            Cesto cestoEscolhido = criarCestoPelaInterface();

            if (cestoEscolhido == null) {
                System.exit(0);
                return;
            }

            new InterfaceGrafica(cestoEscolhido);
        });
    }

    private static Cesto criarCestoPelaInterface() {
        JSpinner spinnerCapacidade = new JSpinner(new SpinnerNumberModel(
                CAPACIDADE_CESTO_PADRAO,
                CAPACIDADE_CESTO_MINIMA,
                null,
                1
        ));
        ((JSpinner.DefaultEditor) spinnerCapacidade.getEditor()).getTextField().setColumns(6);

        JPanel painel = new JPanel(new GridLayout(2, 1, 0, 8));
        painel.add(new JLabel("Capacidade maxima do cesto:"));
        painel.add(spinnerCapacidade);

        while (true) {
            int opcao = JOptionPane.showConfirmDialog(
                    null,
                    painel,
                    "Configuracao do cesto",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (opcao != JOptionPane.OK_OPTION) {
                return null;
            }

            try {
                spinnerCapacidade.commitEdit();
                int capacidade = ((Number) spinnerCapacidade.getValue()).intValue();

                if (capacidade >= CAPACIDADE_CESTO_MINIMA) {
                    return new Cesto(capacidade);
                }
            } catch (ParseException | IllegalArgumentException e) {
                // Mostra a mensagem padrao abaixo.
            }

            JOptionPane.showMessageDialog(
                    null,
                    "Informe uma capacidade maior que zero.",
                    "Capacidade invalida",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    public static void mostraInterfaceGrafica(Cesto cesto) {
        SwingUtilities.invokeLater(() -> new InterfaceGrafica(cesto));
    }
}
