(ns pyjama.io.readers
  "Content extraction utilities for common document types.

  Supported:
  - PDF via Apache PDFBox
  - DOCX via Apache POI
  - EPUB via Epublib + Jsoup (paragraph aggregation)
  - HTML via Jsoup (noise removal and main-content heuristics)
  - Plain text (fallback)

  Primary entry:
  - extract-text: resolves URLs via pyjama.io.core/resolve-path, dispatches by file
    extension, and returns cleaned text."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [pyjama.io.core :as pyo])
  (:import (java.io FileInputStream)
           (java.io File FileInputStream)
           (nl.siegmann.epublib.epub EpubReader)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.text PDFTextStripper)
           (org.apache.poi.xwpf.extractor XWPFWordExtractor)
           (org.apache.poi.xwpf.usermodel XWPFDocument)
           (org.jsoup Jsoup)))

(defn extract-pdf-text [file-path]
  (with-open [doc (Loader/loadPDF ^File (io/file file-path))]
    (let [stripper (PDFTextStripper.)]
      (.getText stripper doc))))

(defn extract-docx-text [file-path]
  (with-open [fis (io/input-stream file-path)
              doc (XWPFDocument. fis)
              extractor (XWPFWordExtractor. doc)]
    (.getText extractor)))

(defn extract-epub-text [file-path]
  (with-open [fis (FileInputStream. (io/file file-path))]
    (let [book (.readEpub (EpubReader.) fis)
          spine-items (-> book .getSpine .getSpineReferences)
          extract-content (fn [item]
                            (let [stream (-> item .getResource .getInputStream)
                                  html (slurp stream)
                                  parsed-html (Jsoup/parse html)
                                  paragraphs (map #(str (.text %)) (.select parsed-html "p"))] ;; Extract paragraphs
                              (clojure.string/join "\n" paragraphs))) ;; Join paragraphs with newlines
          extracted-text (map extract-content spine-items)]
      (clojure.string/join "\n\n" extracted-text))))


;(defn extract-html-text [file-path]
; (let [html (slurp file-path)
;       doc (Jsoup/parse html)]
;  (.text doc)))

(defn extract-html-text [file-path]
 (let [html (slurp file-path)
       doc  (Jsoup/parse html)]

  ;; Remove noise
  (doseq [selector ["script" "style" "nav" "footer" "header" "aside" "form" "noscript" "svg"]]
   (doseq [el (.select doc selector)]
    (.remove el)))

  ;; Try selecting main content blocks
  (let [content (.select doc "main, article, .content, .post, .article, .entry, .markdown-body")
        text (if (and content (not (.isEmpty content)))
              (->> content
                   (map #(.text %))
                   (clojure.string/join "\n\n"))
              ;; fallback to body text
              (.text (.body doc)))]

   ;; Normalize & clean
   (-> text
       (clojure.string/replace #"\s+\n" "\n")         ; remove trailing spaces on lines
       (clojure.string/replace #"\n{3,}" "\n\n")      ; limit excessive line breaks
       (clojure.string/replace #"[ \t]+" " ")         ; normalize spaces
       (clojure.string/trim)))))

(defn extract-text [path]
 (let [resolved-path (pyo/resolve-path path)
       handlers {#{"pdf"}        extract-pdf-text
                 #{"epub"}       extract-epub-text
                 #{"doc" "docx"} extract-docx-text
                 #{"html" "htm"} extract-html-text}
       ext (some #(when (some (fn [x] (str/ends-with? resolved-path x)) %) %) (keys handlers))]
  (if ext
   ((get handlers ext slurp) resolved-path)
   (slurp resolved-path))))