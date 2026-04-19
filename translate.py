import os
import glob

translations = {
    "ru": """
    <string name="signature_studio">Студия подписей</string>
    <string name="draw_here">Рисуйте здесь…</string>
    <string name="size">Размер</string>
    <string name="neon_glow">Неоновое свечение</string>
    <string name="apply_signature">Применить подпись</string>
    <string name="save_failed">Ошибка сохранения!</string>
    <string name="leica_exif_border">Рамка Leica EXIF</string>
    <string name="leica_exif_border_desc">Автоматически добавляет профессиональную рамку с метаданными EXIF внизу экспортируемых изображений. Примечание: Рамка не отображается в окне предварительного просмотра.</string>""",
    "zh-rTW": """
    <string name="signature_studio">簽名工作室</string>
    <string name="draw_here">在此繪製…</string>
    <string name="size">大小</string>
    <string name="neon_glow">霓虹發光</string>
    <string name="apply_signature">套用簽名</string>
    <string name="save_failed">儲存失敗！</string>
    <string name="leica_exif_border">Leica EXIF 邊框</string>
    <string name="leica_exif_border_desc">自動在匯出圖片底部加入專業的 EXIF 中繼資料邊框。注意：為提升效能，預覽視窗中不顯示該邊框。</string>""",
    "zh-rCN": """
    <string name="signature_studio">签名工作室</string>
    <string name="draw_here">在此绘制…</string>
    <string name="size">大小</string>
    <string name="neon_glow">霓虹发光</string>
    <string name="apply_signature">应用签名</string>
    <string name="save_failed">保存失败！</string>
    <string name="leica_exif_border">Leica EXIF 边框</string>
    <string name="leica_exif_border_desc">自动在导出图片底部加入专业的 EXIF 元数据边框。注意：为提升性能，预览窗口中不显示该边框。</string>""",
    "it": """
    <string name="signature_studio">Studio Firme</string>
    <string name="draw_here">Disegna qui…</string>
    <string name="size">Dimensione</string>
    <string name="neon_glow">Bagliore Neon</string>
    <string name="apply_signature">Applica Firma</string>
    <string name="save_failed">Salvataggio fallito!</string>
    <string name="leica_exif_border">Bordo Leica EXIF</string>
    <string name="leica_exif_border_desc">Aggiunge automaticamente un bordo professionale con i metadati EXIF in fondo alle immagini esportate. Nota: Il bordo non viene mostrato nell\'anteprima per ottimizzare le prestazioni.</string>""",
    "ja": """
    <string name="signature_studio">署名スタジオ</string>
    <string name="draw_here">ここに描画…</string>
    <string name="size">サイズ</string>
    <string name="neon_glow">ネオングロー</string>
    <string name="apply_signature">署名を適用</string>
    <string name="save_failed">保存に失敗しました！</string>
    <string name="leica_exif_border">Leica EXIF ボーダー</string>
    <string name="leica_exif_border_desc">エクスポートされた画像の下部にプロフェッショナルな EXIF メタデータボーダーを自動的に追加します。注：パフォーマンスを最適化するため、プレビューウィンドウにボーダーは表示されません。</string>""",
    "de-rDE": """
    <string name="signature_studio">Signatur-Studio</string>
    <string name="draw_here">Hier zeichnen…</string>
    <string name="size">Größe</string>
    <string name="neon_glow">Neon-Leuchten</string>
    <string name="apply_signature">Signatur anwenden</string>
    <string name="save_failed">Speichern fehlgeschlagen!</string>
    <string name="leica_exif_border">Leica EXIF Rand</string>
    <string name="leica_exif_border_desc">Fügt den exportierten Bildern unten automatisch einen professionellen EXIF-Metadatenrand hinzu. Hinweis: Der Rand wird im Vorschaufenster aus Leistungsgründen nicht angezeigt.</string>""",
    "pt-rBR": """
    <string name="signature_studio">Estúdio de Assinaturas</string>
    <string name="draw_here">Desenhe aqui…</string>
    <string name="size">Tamanho</string>
    <string name="neon_glow">Brilho Neon</string>
    <string name="apply_signature">Aplicar Assinatura</string>
    <string name="save_failed">Falha ao salvar!</string>
    <string name="leica_exif_border">Borda Leica EXIF</string>
    <string name="leica_exif_border_desc">Adiciona automaticamente uma borda profissional com metadados EXIF na parte inferior das imagens exportadas. Nota: A borda não é exibida na janela de visualização para otimizar o desempenho.</string>""",
    "es": """
    <string name="signature_studio">Estudio de Firmas</string>
    <string name="draw_here">Dibuja aquí…</string>
    <string name="size">Tamaño</string>
    <string name="neon_glow">Brillo Neón</string>
    <string name="apply_signature">Aplicar Firma</string>
    <string name="save_failed">¡Error al guardar!</string>
    <string name="leica_exif_border">Borde Leica EXIF</string>
    <string name="leica_exif_border_desc">Añade automáticamente un borde profesional con metadatos EXIF en la parte inferior de las imágenes exportadas. Nota: El borde no se muestra en la ventana de previsualización para optimizar el rendimiento.</string>""",
    "fr": """
    <string name="signature_studio">Studio de Signature</string>
    <string name="draw_here">Dessinez ici…</string>
    <string name="size">Taille</string>
    <string name="neon_glow">Lueur Néon</string>
    <string name="apply_signature">Appliquer la Signature</string>
    <string name="save_failed">Échec de la sauvegarde !</string>
    <string name="leica_exif_border">Bordure Leica EXIF</string>
    <string name="leica_exif_border_desc">Ajoute automatiquement une bordure de métadonnées EXIF professionnelle au bas des images exportées. Remarque : La bordure n'est pas affichée dans la fenêtre d'aperçu pour optimiser les performances.</string>""",
    "nn": """
    <string name="signature_studio">Signaturstudio</string>
    <string name="draw_here">Teikn her…</string>
    <string name="size">Storleik</string>
    <string name="neon_glow">Neonglød</string>
    <string name="apply_signature">Bruk Signatur</string>
    <string name="save_failed">Lagring feila!</string>
    <string name="leica_exif_border">Leica EXIF-kant</string>
    <string name="leica_exif_border_desc">Legg automatisk til ein profesjonell EXIF-metadatakant nedst på eksporterte bilete. Merk: Kanten blir ikkje vist i førehandsvisninga for å optimalisere ytinga.</string>""",
    "nb-rNO": """
    <string name="signature_studio">Signaturstudio</string>
    <string name="draw_here">Tegn her…</string>
    <string name="size">Størrelse</string>
    <string name="neon_glow">Neonglød</string>
    <string name="apply_signature">Bruk Signatur</string>
    <string name="save_failed">Lagring feilet!</string>
    <string name="leica_exif_border">Leica EXIF-kant</string>
    <string name="leica_exif_border_desc">Legger automatisk til en profesjonell EXIF-metadatakant nederst på eksporterte bilder. Merk: Kanten vises ikke i forhåndsvisningen for å optimalisere ytelsen.</string>""",
    "pt": """
    <string name="signature_studio">Estúdio de Assinaturas</string>
    <string name="draw_here">Desenhe aqui…</string>
    <string name="size">Tamanho</string>
    <string name="neon_glow">Brilho Neon</string>
    <string name="apply_signature">Aplicar Assinatura</string>
    <string name="save_failed">Falha ao guardar!</string>
    <string name="leica_exif_border">Bordo Leica EXIF</string>
    <string name="leica_exif_border_desc">Adiciona automaticamente um bordo profissional com metadados EXIF no fundo das imagens exportadas. Nota: O bordo não é exibido na janela de visualização para otimizar o desempenho.</string>"""
}

original_block = """
    <string name="signature_studio">Signature Studio</string>
    <string name="draw_here">Draw here…</string>
    <string name="size">Size</string>
    <string name="neon_glow">Neon Glow</string>
    <string name="apply_signature">Apply Signature</string>
    <string name="save_failed">Save failed!</string>
    <string name="leica_exif_border">Leica EXIF Border</string>
    <string name="leica_exif_border_desc">Automatically appends a professional EXIF metadata border at the bottom of exported images. Note: The border is not shown in the preview window to optimize performance.</string>
"""

files = glob.glob("app/src/main/res/values-*/strings.xml")
for f in files:
    code = f.split("values-")[1].split("/")[0]
    if code in translations:
        with open(f, "r") as file:
            content = file.read()
            
        if "signature_studio" in content and "Signature Studio" in content:
            new_content = content.replace(original_block.strip() + "\n", translations[code].strip() + "\n")
            with open(f, "w") as file:
                file.write(new_content)
            print(f"Translated {f}")
