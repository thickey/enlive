;   Copyright (c) Christophe Grand, 2009. All rights reserved.

;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this 
;   distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns net.cgrand.enlive-html.test
  (:use net.cgrand.enlive-html)
  (:require [net.cgrand.xml :as xml])
  (:require [clojure.zip :as z])
  (:require [net.cgrand.enlive-html.state-machine :as sm])
  (:use [clojure.contrib.test-is :as test-is :only [set-test is are]]))

;; test utilities
(defn- htmlize* [node]
  (cond
    (xml/tag? node)
      (-> node
        (assoc-in [:attrs :class] (attr-values node :class))
        (update-in [:content] (comp htmlize* seq)))
    (or (coll? node) (seq? node))
      (map htmlize* node)
    :else node))

(defn- htmlize [s]
  (htmlize* 
    (if (string? s)
      (html-resource (java.io.StringReader. s))
      s))) 

(defn html-src [s]
  (first (html-resource (java.io.StringReader. s))))

(defn- same? [& xs]
  (apply = (map htmlize xs)))

(defmacro #^{:private true} 
 is-same
 [& forms]
 `(is (same? ~@forms)))

(defmacro sniptest
 "A handy macro for experimenting at the repl" 
 [source-string & forms]
  `(apply str (emit* ((transformation ~@forms) (html-src ~source-string))))) 

(defn- test-step [expected state node]
  (= expected (boolean (sm/accept? (sm/step state (xml/xml-zip node))))))

(defn- elt 
 ([tag] (elt tag nil))
 ([tag attrs & content]
   {:tag tag
    :attrs attrs
    :content content}))



(set-test net.cgrand.enlive-html/compile-keyword
  (are (= _2 (@#'net.cgrand.enlive-html/compile-keyword _1))
    :foo `(tag= :foo)
    :* `any
    :#id `(id= "id")
    :.class1 `(has-class "class1")
    :foo#bar.baz1.baz2 `(sm/intersection (tag= :foo) (id= "bar") (has-class "baz1" "baz2"))))

(set-test tag=
  (are (test-step _1 _2 _3)
    true (tag= :foo) (elt :foo)
    false (tag= :bar) (elt :foo)))

(set-test id=
  (are (test-step _1 _2 _3)
    true (id= "foo") (elt :div {:id "foo"})
    false (id= "bar") (elt :div {:id "foo"})
    false (id= "foo") (elt :div)))

(set-test attr? 
  (are (test-step _1 _2 _3)
    true (attr? :href) (elt :a {:href "http://cgrand.net/"})
    false (attr? :href) (elt :a {:name "toc"})
    false (attr? :href :title) (elt :a {:href "http://cgrand.net/"})
    true (attr? :href :title) (elt :a {:href "http://cgrand.net/" :title "home"})))
    
(set-test attr= 
  (are (test-step _1 _2 (elt :a {:href "http://cgrand.net/" :title "home"}))
    true (attr= :href "http://cgrand.net/")
    false (attr= :href "http://clojure.org/")
    false (attr= :href "http://cgrand.net/" :name "home") 
    false (attr= :href "http://cgrand.net/" :title "homepage")
    true (attr= :href "http://cgrand.net/" :title "home")))
    
(set-test attr-starts
  (are (test-step _1 _2 (elt :a {:href "http://cgrand.net/" :title "home"}))
    true (attr-starts :href "http://cgr")
    false (attr-starts :href "http://clo")
    false (attr-starts :href "http://cgr" :name "ho")
    false (attr-starts :href "http://cgr" :title "x") 
    true (attr-starts :href "http://cgr" :title "ho")))

(set-test attr-ends
  (are (test-step _1 _2 (elt :a {:href "http://cgrand.net/" :title "home"}))
    true (attr-ends :href "d.net/")
    false (attr-ends :href "e.org/")
    false (attr-ends :href "d.net/" :name "me")
    false (attr-ends :href "d.net/" :title "hom")
    true (attr-ends :href "d.net/" :title "me")))

(set-test attr-contains
  (are (test-step _1 _2 (elt :a {:href "http://cgrand.net/" :title "home"}))
    true (attr-contains :href "rand")
    false (attr-contains :href "jure")
    false (attr-contains :href "rand" :name "om") 
    false (attr-contains :href "rand" :title "pa")
    true (attr-contains :href "rand" :title "om")))

(set-test nth-child
  (are (same? _2 (at (html-src "<dl><dt>1<dt>2<dt>3<dt>4<dt>5") _1 (add-class "foo")))    
    [[:dt (nth-child 2)]] "<dl><dt>1<dt class=foo>2<dt>3<dt>4<dt>5" 
    [[:dt (nth-child 2 0)]] "<dl><dt>1<dt class=foo>2<dt>3<dt class=foo>4<dt>5" 
    [[:dt (nth-child 3 1)]] "<dl><dt class=foo>1<dt>2<dt>3<dt class=foo>4<dt>5" 
    [[:dt (nth-child -1 3)]] "<dl><dt class=foo>1<dt class=foo>2<dt class=foo>3<dt>4<dt>5" 
    [[:dt (nth-child 3 -1)]] "<dl><dt>1<dt class=foo>2<dt>3<dt>4<dt class=foo>5"))
      
(set-test nth-last-child
  (are (same? _2 (at (html-src "<dl><dt>1<dt>2<dt>3<dt>4<dt>5") _1 (add-class "foo")))    
    [[:dt (nth-last-child 2)]] "<dl><dt>1<dt>2<dt>3<dt class=foo>4<dt>5" 
    [[:dt (nth-last-child 2 0)]] "<dl><dt>1<dt class=foo>2<dt>3<dt class=foo>4<dt>5" 
    [[:dt (nth-last-child 3 1)]] "<dl><dt>1<dt class=foo>2<dt>3<dt>4<dt class=foo>5" 
    [[:dt (nth-last-child -1 3)]] "<dl><dt>1<dt>2<dt class=foo>3<dt class=foo>4<dt class=foo>5" 
    [[:dt (nth-last-child 3 -1)]] "<dl><dt class=foo>1<dt>2<dt>3<dt class=foo>4<dt>5"))

(set-test nth-of-type
  (are (same? _2 (at (html-src "<dl><dt>1<dd>def #1<dt>2<dt>3<dd>def #3<dt>4<dt>5") _1 (add-class "foo")))    
    [[:dt (nth-of-type 2)]] "<dl><dt>1<dd>def #1<dt class=foo>2<dt>3<dd>def #3<dt>4<dt>5" 
    [[:dt (nth-of-type 2 0)]] "<dl><dt>1<dd>def #1<dt class=foo>2<dt>3<dd>def #3<dt class=foo>4<dt>5" 
    [[:dt (nth-of-type 3 1)]] "<dl><dt class=foo>1<dd>def #1<dt>2<dt>3<dd>def #3<dt class=foo>4<dt>5" 
    [[:dt (nth-of-type -1 3)]] "<dl><dt class=foo>1<dd>def #1<dt class=foo>2<dt class=foo>3<dd>def #3<dt>4<dt>5" 
    [[:dt (nth-of-type 3 -1)]] "<dl><dt>1<dd>def #1<dt class=foo>2<dt>3<dd>def #3<dt>4<dt class=foo>5"))
   
(set-test nth-last-of-type
  (are (same? _2 (at (html-src "<dl><dt>1<dd>def #1<dt>2<dt>3<dd>def #3<dt>4<dt>5") _1 (add-class "foo")))    
    [[:dt (nth-last-of-type 2)]] "<dl><dt>1<dd>def #1<dt>2<dt>3<dd>def #3<dt class=foo>4<dt>5" 
    [[:dt (nth-last-of-type 2 0)]] "<dl><dt>1<dd>def #1<dt class=foo>2<dt>3<dd>def #3<dt class=foo>4<dt>5" 
    [[:dt (nth-last-of-type 3 1)]] "<dl><dt>1<dd>def #1<dt class=foo>2<dt>3<dd>def #3<dt>4<dt class=foo>5" 
    [[:dt (nth-last-of-type -1 3)]] "<dl><dt>1<dd>def #1<dt>2<dt class=foo>3<dd>def #3<dt class=foo>4<dt class=foo>5" 
    [[:dt (nth-last-of-type 3 -1)]] "<dl><dt class=foo>1<dd>def #1<dt>2<dt>3<dd>def #3<dt class=foo>4<dt>5"))
    
(set-test has    
  (is-same "<div><p>XXX<p class='ok'><a>link</a><p>YYY" 
    (at (html-src "<div><p>XXX<p><a>link</a><p>YYY") 
      [[:p (has [:a])]] (add-class "ok"))))

(set-test but    
  (is-same "<div><p>XXX<p><a class='ok'>link</a><p>YYY" 
    (at (html-src "<div><p>XXX<p><a>link</a><p>YYY") 
      [:div (but :p)] (add-class "ok")))
      
  (is-same "<div><p class='ok'>XXX<p><a>link</a><p class='ok'>YYY" 
    (at (html-src "<div><p>XXX<p><a>link</a><p>YYY") 
      [[:p (but (has [:a]))]] (add-class "ok"))))

(set-test left
  (are (same? _2 (at (html-src "<h1>T1<h2>T2<h3>T3<p>XXX") _1 (add-class "ok"))) 
    [[:h3 (left :h2)]] "<h1>T1<h2>T2<h3 class=ok>T3<p>XXX" 
    [[:h3 (left :h1)]] "<h1>T1<h2>T2<h3>T3<p>XXX" 
    [[:h3 (left :p)]] "<h1>T1<h2>T2<h3>T3<p>XXX"))

(set-test lefts
  (are (same? _2 (at (html-src "<h1>T1<h2>T2<h3>T3<p>XXX") _1 (add-class "ok"))) 
    [[:h3 (lefts :h2)]] "<h1>T1<h2>T2<h3 class=ok>T3<p>XXX" 
    [[:h3 (lefts :h1)]] "<h1>T1<h2>T2<h3 class=ok>T3<p>XXX" 
    [[:h3 (lefts :p)]] "<h1>T1<h2>T2<h3>T3<p>XXX")) 
      
(set-test right
  (are (same? _2 (at (html-src "<h1>T1<h2>T2<h3>T3<p>XXX") _1 (add-class "ok"))) 
    [[:h2 (right :h3)]] "<h1>T1<h2 class=ok>T2<h3>T3<p>XXX" 
    [[:h2 (right :p)]] "<h1>T1<h2>T2<h3>T3<p>XXX" 
    [[:h2 (right :h1)]] "<h1>T1<h2>T2<h3>T3<p>XXX")) 

(set-test rights  
  (are (same? _2 (at (html-src "<h1>T1<h2>T2<h3>T3<p>XXX") _1 (add-class "ok"))) 
    [[:h2 (rights :h3)]] "<h1>T1<h2 class=ok>T2<h3>T3<p>XXX" 
    [[:h2 (rights :p)]] "<h1>T1<h2 class=ok>T2<h3>T3<p>XXX" 
    [[:h2 (rights :h1)]] "<h1>T1<h2>T2<h3>T3<p>XXX")) 

(set-test any-node 
  (is (= 3 (count (select (htmlize "<i>this</i> is a <i>test</i>") [:body :> any-node])))))  

(set-test transform
  (is-same "<div>" (at (html-src "<div><span>") [:span] nil))
  (is-same "<!-- comment -->" (at (html-src "<!-- comment -->") [:span] nil)))
  
(set-test clone-for
  (is-same "<ul><li>one<li>two" (at (html-src "<ul><li>") [:li] (clone-for [x ["one" "two"]] (content x))))) 

(set-test move
  (are (same? _2 ((move [:span] [:div] _1) (html-src "<span>1</span><div id=target>here</div><span>2</span>")))
  substitute "<span>1</span><span>2</span>"
  content "<div id=target><span>1</span><span>2</span></div>"
  after "<div id=target>here</div><span>1</span><span>2</span>"
  before "<span>1</span><span>2</span><div id=target>here</div>"
  append "<div id=target>here<span>1</span><span>2</span></div>"
  prepend "<div id=target><span>1</span><span>2</span>here</div>"))
  
(set-test select 
  (is (= 3 (count (select (htmlize "<h1>hello</h1>") [:*])))))
  
(set-test emit*
  (is (= "<html><body><h1>hello&lt;<script>if (im < bad) document.write('&lt;')</script></h1></body></html>"
        (sniptest "<h1>hello&lt;<script>if (im < bad) document.write('&lt;')"))))
