import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { environment } from '../environments/environment';

@Component({
  selector: 'orbix-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet></router-outlet>`
})
export class AppComponent implements OnInit {
  private readonly title = inject(Title);

  ngOnInit(): void {
    this.title.setTitle(environment.appName);
  }
}
